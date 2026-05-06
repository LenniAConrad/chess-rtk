/*
 * native/oneapi/lc0_bt4_oneapi_jni.cpp
 *
 * Optional oneAPI/SYCL backend for LC0 BT4 attention-body inference.
 */

#include <jni.h>
#include <sycl/sycl.hpp>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iterator>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr int MAGIC = 0x4A345442;
constexpr int VERSION = 1;
constexpr int TOKENS = 64;
constexpr int INPUT_CHANNELS = 112;
constexpr int POLICY_SIZE = 1858;
constexpr int INTERNAL_POLICY_SIZE = 67 * 64;
constexpr int FROM_TO_POLICY_SIZE = 64 * 64;

enum Activation {
    ACT_NONE = 0,
    ACT_RELU = 1,
    ACT_MISH = 2
};

static std::string to_lower(std::string v) {
    for (char& c : v) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return v;
}

static bool is_intel_gpu(const sycl::device& dev) {
    if (!dev.is_gpu()) return false;
    std::string vendor = to_lower(dev.get_info<sycl::info::device::vendor>());
    return vendor.find("intel") != std::string::npos;
}

static std::vector<sycl::device> intel_gpus() {
    std::vector<sycl::device> out;
    try {
        auto devices = sycl::device::get_devices(sycl::info::device_type::gpu);
        for (const auto& dev : devices) {
            if (is_intel_gpu(dev)) out.push_back(dev);
        }
    } catch (const sycl::exception&) {
        return {};
    }
    return out;
}

static int device_count() {
    return static_cast<int>(intel_gpus().size());
}

struct Dense {
    int inDim = 0;
    int outDim = 0;
    std::vector<float> weights;
    std::vector<float> bias;
    float* dWeights = nullptr;
    float* dBias = nullptr;
};

struct Attention {
    int heads = 0;
    Dense query;
    Dense key;
    Dense value;
    Dense out;
};

struct Block {
    Attention attention;
    Dense ffnIn;
    Dense ffnOut;
    std::vector<float> ln1Gamma;
    std::vector<float> ln1Beta;
    std::vector<float> ln2Gamma;
    std::vector<float> ln2Beta;
    float* dLn1Gamma = nullptr;
    float* dLn1Beta = nullptr;
    float* dLn2Gamma = nullptr;
    float* dLn2Beta = nullptr;
    int activation = ACT_MISH;
    float alpha = 1.0f;
};

struct PolicyHead {
    Dense embedding;
    std::vector<Block> encoders;
    Dense query;
    Dense key;
    std::vector<float> promotionWeights;
    float* dPromotionWeights = nullptr;
    int activation = ACT_MISH;
};

struct ValueHead {
    Dense embedding;
    Dense fc1;
    Dense fc2;
    int activation = ACT_MISH;
};

struct Net {
    std::unique_ptr<sycl::queue> queue;
    std::string name;
    bool peMap = true;
    int inputChannels = INPUT_CHANNELS;
    int tokens = TOKENS;
    int embedding = 0;
    int encoderLayers = 0;
    int heads = 0;
    int policySize = POLICY_SIZE;
    float eps = 1.0e-6f;
    long long parameterCount = 0;
    Dense inputEmbedding;
    std::vector<Block> encoders;
    PolicyHead policy;
    ValueHead value;
    std::vector<int> policyMap;
    int* dPolicyMap = nullptr;
};

class Reader {
public:
    explicit Reader(const std::string& path) {
        std::ifstream in(path, std::ios::binary);
        if (!in) throw std::runtime_error("open failed");
        data.assign(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
    }

    int32_t i32() {
        need(4);
        uint32_t v = static_cast<unsigned char>(data[pos])
                | (static_cast<uint32_t>(static_cast<unsigned char>(data[pos + 1])) << 8)
                | (static_cast<uint32_t>(static_cast<unsigned char>(data[pos + 2])) << 16)
                | (static_cast<uint32_t>(static_cast<unsigned char>(data[pos + 3])) << 24);
        pos += 4;
        return static_cast<int32_t>(v);
    }

    float f32() {
        uint32_t bits = static_cast<uint32_t>(i32());
        float out;
        std::memcpy(&out, &bits, sizeof(out));
        return out;
    }

    std::string str() {
        int n = i32();
        if (n < 0 || n > 1000000) throw std::runtime_error("bad string");
        need(static_cast<size_t>(n));
        std::string out(data.data() + pos, data.data() + pos + n);
        pos += static_cast<size_t>(n);
        return out;
    }

    std::vector<float> floats() {
        int n = i32();
        if (n < 0) throw std::runtime_error("bad floats");
        std::vector<float> out(static_cast<size_t>(n));
        for (int i = 0; i < n; ++i) out[static_cast<size_t>(i)] = f32();
        return out;
    }

    bool done() const {
        return pos == data.size();
    }

private:
    std::vector<char> data;
    size_t pos = 0;

    void need(size_t n) {
        if (pos + n > data.size()) throw std::runtime_error("eof");
    }
};

static int parse_activation(const std::string& s) {
    if (s == "NONE") return ACT_NONE;
    if (s == "RELU") return ACT_RELU;
    if (s == "MISH") return ACT_MISH;
    throw std::runtime_error("bad activation");
}

static Dense read_dense(Reader& in) {
    Dense dense;
    dense.inDim = in.i32();
    dense.outDim = in.i32();
    dense.weights = in.floats();
    dense.bias = in.floats();
    if (dense.inDim <= 0 || dense.outDim <= 0
            || dense.weights.size() != static_cast<size_t>(dense.inDim) * dense.outDim
            || dense.bias.size() != static_cast<size_t>(dense.outDim)) {
        throw std::runtime_error("bad dense");
    }
    return dense;
}

static Attention read_attention(Reader& in) {
    Attention attention;
    attention.heads = in.i32();
    attention.query = read_dense(in);
    attention.key = read_dense(in);
    attention.value = read_dense(in);
    attention.out = read_dense(in);
    return attention;
}

static Block read_block(Reader& in) {
    Block block;
    block.attention = read_attention(in);
    block.ffnIn = read_dense(in);
    block.ffnOut = read_dense(in);
    block.ln1Gamma = in.floats();
    block.ln1Beta = in.floats();
    block.ln2Gamma = in.floats();
    block.ln2Beta = in.floats();
    block.activation = parse_activation(in.str());
    block.alpha = in.f32();
    return block;
}

static std::vector<Block> read_blocks(Reader& in) {
    int count = in.i32();
    if (count < 0) throw std::runtime_error("bad block count");
    std::vector<Block> blocks;
    blocks.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) blocks.push_back(read_block(in));
    return blocks;
}

static bool queen_like_or_knight(int from, int to) {
    int df = (to & 7) - (from & 7);
    int dr = (to >> 3) - (from >> 3);
    if (df == 0 || dr == 0 || std::abs(df) == std::abs(dr)) return true;
    int adf = std::abs(df);
    int adr = std::abs(dr);
    return (adf == 1 && adr == 2) || (adf == 2 && adr == 1);
}

static std::vector<int> build_policy_map() {
    std::vector<int> map(INTERNAL_POLICY_SIZE, -1);
    int next = 0;
    for (int from = 0; from < 64; ++from) {
        for (int to = 0; to < 64; ++to) {
            if (from != to && queen_like_or_knight(from, to)) map[static_cast<size_t>(from * 64 + to)] = next++;
        }
    }
    for (int fromFile = 0; fromFile < 8; ++fromFile) {
        for (int toFile = std::max(0, fromFile - 1); toFile <= std::min(7, fromFile + 1); ++toFile) {
            for (int promo = 0; promo < 3; ++promo) {
                map[static_cast<size_t>(FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo)] = next++;
            }
        }
    }
    if (next != POLICY_SIZE) throw std::runtime_error("bad policy map");
    return map;
}

static long long dense_params(const Dense& dense) {
    return static_cast<long long>(dense.weights.size() + dense.bias.size());
}

static long long attention_params(const Attention& attention) {
    return dense_params(attention.query) + dense_params(attention.key)
            + dense_params(attention.value) + dense_params(attention.out);
}

static long long block_params(const Block& block) {
    return attention_params(block.attention) + dense_params(block.ffnIn) + dense_params(block.ffnOut)
            + static_cast<long long>(block.ln1Gamma.size() + block.ln1Beta.size()
                    + block.ln2Gamma.size() + block.ln2Beta.size());
}

static long long compute_params(const Net& net) {
    long long total = dense_params(net.inputEmbedding);
    for (const auto& block : net.encoders) total += block_params(block);
    total += dense_params(net.policy.embedding) + dense_params(net.policy.query) + dense_params(net.policy.key)
            + static_cast<long long>(net.policy.promotionWeights.size());
    for (const auto& block : net.policy.encoders) total += block_params(block);
    total += dense_params(net.value.embedding) + dense_params(net.value.fc1) + dense_params(net.value.fc2);
    return total;
}

static bool upload_floats(Net& net, const std::vector<float>& host, float** device) {
    *device = nullptr;
    if (host.empty()) return true;
    try {
        *device = sycl::malloc_device<float>(host.size(), *net.queue);
        net.queue->memcpy(*device, host.data(), host.size() * sizeof(float)).wait_and_throw();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool upload_ints(Net& net, const std::vector<int>& host, int** device) {
    *device = nullptr;
    if (host.empty()) return true;
    try {
        *device = sycl::malloc_device<int>(host.size(), *net.queue);
        net.queue->memcpy(*device, host.data(), host.size() * sizeof(int)).wait_and_throw();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool upload_dense(Net& net, Dense& dense) {
    return upload_floats(net, dense.weights, &dense.dWeights) && upload_floats(net, dense.bias, &dense.dBias);
}

static bool upload_attention(Net& net, Attention& attention) {
    return upload_dense(net, attention.query) && upload_dense(net, attention.key)
            && upload_dense(net, attention.value) && upload_dense(net, attention.out);
}

static bool upload_block(Net& net, Block& block) {
    return upload_attention(net, block.attention)
            && upload_dense(net, block.ffnIn)
            && upload_dense(net, block.ffnOut)
            && upload_floats(net, block.ln1Gamma, &block.dLn1Gamma)
            && upload_floats(net, block.ln1Beta, &block.dLn1Beta)
            && upload_floats(net, block.ln2Gamma, &block.dLn2Gamma)
            && upload_floats(net, block.ln2Beta, &block.dLn2Beta);
}

static void free_ptr(Net* net, void* ptr) {
    if (net && net->queue && ptr) sycl::free(ptr, *net->queue);
}

static void release_dense(Net* net, Dense& dense) {
    free_ptr(net, dense.dWeights);
    free_ptr(net, dense.dBias);
    dense.dWeights = nullptr;
    dense.dBias = nullptr;
}

static void release_block(Net* net, Block& block);

static void release_attention(Net* net, Attention& attention) {
    release_dense(net, attention.query);
    release_dense(net, attention.key);
    release_dense(net, attention.value);
    release_dense(net, attention.out);
}

static void release_block(Net* net, Block& block) {
    release_attention(net, block.attention);
    release_dense(net, block.ffnIn);
    release_dense(net, block.ffnOut);
    free_ptr(net, block.dLn1Gamma);
    free_ptr(net, block.dLn1Beta);
    free_ptr(net, block.dLn2Gamma);
    free_ptr(net, block.dLn2Beta);
}

static void release_net(Net* net) {
    if (!net) return;
    release_dense(net, net->inputEmbedding);
    for (auto& block : net->encoders) release_block(net, block);
    release_dense(net, net->policy.embedding);
    for (auto& block : net->policy.encoders) release_block(net, block);
    release_dense(net, net->policy.query);
    release_dense(net, net->policy.key);
    free_ptr(net, net->policy.dPromotionWeights);
    release_dense(net, net->value.embedding);
    release_dense(net, net->value.fc1);
    release_dense(net, net->value.fc2);
    free_ptr(net, net->dPolicyMap);
    delete net;
}

static bool upload_net(Net& net) {
    if (!upload_dense(net, net.inputEmbedding)) return false;
    for (auto& block : net.encoders) if (!upload_block(net, block)) return false;
    if (!upload_dense(net, net.policy.embedding)) return false;
    for (auto& block : net.policy.encoders) if (!upload_block(net, block)) return false;
    if (!upload_dense(net, net.policy.query) || !upload_dense(net, net.policy.key)) return false;
    if (!upload_floats(net, net.policy.promotionWeights, &net.policy.dPromotionWeights)) return false;
    if (!upload_dense(net, net.value.embedding) || !upload_dense(net, net.value.fc1)
            || !upload_dense(net, net.value.fc2)) return false;
    return upload_ints(net, net.policyMap, &net.dPolicyMap);
}

static Net* load_net(const std::string& path) {
    auto* net = new Net();
    try {
        auto devices = intel_gpus();
        if (devices.empty()) throw std::runtime_error("no intel gpu");
        net->queue = std::make_unique<sycl::queue>(devices.front());
        Reader in(path);
        if (in.i32() != MAGIC || in.i32() != VERSION) throw std::runtime_error("bad header");
        net->name = in.str();
        (void) in.str();
        net->peMap = in.str() == "PE_MAP";
        net->inputChannels = in.i32();
        net->tokens = in.i32();
        net->embedding = in.i32();
        net->encoderLayers = in.i32();
        net->heads = in.i32();
        net->policySize = in.i32();
        net->eps = in.f32();
        net->inputEmbedding = read_dense(in);
        net->encoders = read_blocks(in);
        net->policy.embedding = read_dense(in);
        net->policy.encoders = read_blocks(in);
        net->policy.query = read_dense(in);
        net->policy.key = read_dense(in);
        net->policy.promotionWeights = in.floats();
        net->policy.activation = parse_activation(in.str());
        net->value.embedding = read_dense(in);
        net->value.fc1 = read_dense(in);
        net->value.fc2 = read_dense(in);
        net->value.activation = parse_activation(in.str());
        if (!in.done()) throw std::runtime_error("trailing bytes");
        if (net->inputChannels != INPUT_CHANNELS || net->tokens != TOKENS || net->policySize != POLICY_SIZE) {
            throw std::runtime_error("unsupported shape");
        }
        net->policyMap = build_policy_map();
        net->parameterCount = compute_params(*net);
        if (!upload_net(*net)) throw std::runtime_error("upload failed");
        return net;
    } catch (...) {
        release_net(net);
        return nullptr;
    }
}

static bool alloc_float(Net& net, float** ptr, int count) {
    *ptr = nullptr;
    try {
        *ptr = sycl::malloc_device<float>(static_cast<size_t>(count), *net.queue);
        return *ptr != nullptr;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool run_activate(Net& net, float* data, int n, int activation) {
    if (activation == ACT_NONE) return true;
    try {
        net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(n)), [=](sycl::id<1> id) {
            int i = static_cast<int>(id[0]);
            float x = data[i];
            if (activation == ACT_RELU) {
                data[i] = x > 0.0f ? x : 0.0f;
            } else {
                float softplus = x > 20.0f ? x : (x < -20.0f ? sycl::exp(x) : sycl::log(1.0f + sycl::exp(x)));
                data[i] = x * sycl::tanh(softplus);
            }
        }).wait_and_throw();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool run_dense_tokens(Net& net, const float* input, int rows, const Dense& dense, float** out) {
    int total = rows * dense.outDim;
    if (!alloc_float(net, out, total)) return false;
    int inDim = dense.inDim;
    int outDim = dense.outDim;
    const float* weights = dense.dWeights;
    const float* bias = dense.dBias;
    float* output = *out;
    try {
        net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(total)), [=](sycl::id<1> id) {
            int i = static_cast<int>(id[0]);
            int row = i / outDim;
            int col = i - row * outDim;
            float sum = bias[col];
            const float* in = input + row * inDim;
            const float* w = weights + col * inDim;
            for (int k = 0; k < inDim; ++k) sum += w[k] * in[k];
            output[i] = sum;
        }).wait_and_throw();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool run_dense_vector(Net& net, const float* input, const Dense& dense, float** out) {
    if (!alloc_float(net, out, dense.outDim)) return false;
    int inDim = dense.inDim;
    int outDim = dense.outDim;
    const float* weights = dense.dWeights;
    const float* bias = dense.dBias;
    float* output = *out;
    try {
        net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(outDim)), [=](sycl::id<1> id) {
            int col = static_cast<int>(id[0]);
            float sum = bias[col];
            const float* w = weights + col * inDim;
            for (int k = 0; k < inDim; ++k) sum += w[k] * input[k];
            output[col] = sum;
        }).wait_and_throw();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

static bool run_attention(Net& net, const float* input, const Block& block, int tokens, float** out) {
    float* q = nullptr;
    float* k = nullptr;
    float* v = nullptr;
    float* combined = nullptr;
    bool ok = run_dense_tokens(net, input, tokens, block.attention.query, &q)
            && run_dense_tokens(net, input, tokens, block.attention.key, &k)
            && run_dense_tokens(net, input, tokens, block.attention.value, &v)
            && alloc_float(net, &combined, tokens * block.attention.query.outDim);
    if (ok) {
        int dModel = block.attention.query.outDim;
        int heads = block.attention.heads;
        int total = tokens * dModel;
        try {
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(total)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                int depth = dModel / heads;
                int d = i % depth;
                int head = (i / depth) % heads;
                int queryToken = i / (depth * heads);
                int headOffset = head * depth;
                float invScale = sycl::rsqrt(static_cast<float>(depth));
                float maxScore = -3.402823466e+38F;
                for (int keyToken = 0; keyToken < tokens; ++keyToken) {
                    float sum = 0.0f;
                    int qb = queryToken * dModel + headOffset;
                    int kb = keyToken * dModel + headOffset;
                    for (int x = 0; x < depth; ++x) sum += q[qb + x] * k[kb + x];
                    float score = sum * invScale;
                    maxScore = sycl::fmax(maxScore, score);
                }
                float denom = 0.0f;
                float weighted = 0.0f;
                for (int keyToken = 0; keyToken < tokens; ++keyToken) {
                    float sum = 0.0f;
                    int qb = queryToken * dModel + headOffset;
                    int kb = keyToken * dModel + headOffset;
                    for (int x = 0; x < depth; ++x) sum += q[qb + x] * k[kb + x];
                    float e = sycl::exp(sum * invScale - maxScore);
                    denom += e;
                    weighted += e * v[keyToken * dModel + headOffset + d];
                }
                combined[queryToken * dModel + headOffset + d] = denom > 0.0f ? weighted / denom : 0.0f;
            }).wait_and_throw();
            ok = run_dense_tokens(net, combined, tokens, block.attention.out, out);
        } catch (const sycl::exception&) {
            ok = false;
        }
    }
    free_ptr(&net, q);
    free_ptr(&net, k);
    free_ptr(&net, v);
    free_ptr(&net, combined);
    return ok;
}

static bool run_encoder_block(Net& net, float* input, const Block& block, int tokens, float eps, float** out) {
    int embedding = block.attention.out.outDim;
    int elements = tokens * embedding;
    float* attended = nullptr;
    float* hidden = nullptr;
    float* ffnOut = nullptr;
    bool ok = run_attention(net, input, block, tokens, &attended);
    try {
        if (ok) {
            float alpha = block.alpha;
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(elements)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                attended[i] = attended[i] * alpha + input[i];
            }).wait_and_throw();
            const float* ln1Gamma = block.dLn1Gamma;
            const float* ln1Beta = block.dLn1Beta;
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(tokens)), [=](sycl::id<1> id) {
                int row = static_cast<int>(id[0]);
                float* base = attended + row * embedding;
                float mean = 0.0f;
                for (int i = 0; i < embedding; ++i) mean += base[i];
                mean /= static_cast<float>(embedding);
                float variance = 0.0f;
                for (int i = 0; i < embedding; ++i) {
                    float centered = base[i] - mean;
                    variance += centered * centered;
                }
                float invStd = sycl::rsqrt(variance / static_cast<float>(embedding) + eps);
                for (int i = 0; i < embedding; ++i) {
                    base[i] = (base[i] - mean) * invStd * ln1Gamma[i] + ln1Beta[i];
                }
            }).wait_and_throw();
        }
    } catch (const sycl::exception&) {
        ok = false;
    }
    if (ok) ok = run_dense_tokens(net, attended, tokens, block.ffnIn, &hidden);
    if (ok) ok = run_activate(net, hidden, tokens * block.ffnIn.outDim, block.activation);
    if (ok) ok = run_dense_tokens(net, hidden, tokens, block.ffnOut, &ffnOut);
    try {
        if (ok) {
            float alpha = block.alpha;
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(elements)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                ffnOut[i] = ffnOut[i] * alpha + attended[i];
            }).wait_and_throw();
            const float* ln2Gamma = block.dLn2Gamma;
            const float* ln2Beta = block.dLn2Beta;
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(tokens)), [=](sycl::id<1> id) {
                int row = static_cast<int>(id[0]);
                float* base = ffnOut + row * embedding;
                float mean = 0.0f;
                for (int i = 0; i < embedding; ++i) mean += base[i];
                mean /= static_cast<float>(embedding);
                float variance = 0.0f;
                for (int i = 0; i < embedding; ++i) {
                    float centered = base[i] - mean;
                    variance += centered * centered;
                }
                float invStd = sycl::rsqrt(variance / static_cast<float>(embedding) + eps);
                for (int i = 0; i < embedding; ++i) {
                    base[i] = (base[i] - mean) * invStd * ln2Gamma[i] + ln2Beta[i];
                }
            }).wait_and_throw();
        }
    } catch (const sycl::exception&) {
        ok = false;
    }
    free_ptr(&net, attended);
    free_ptr(&net, hidden);
    if (ok) *out = ffnOut;
    else free_ptr(&net, ffnOut);
    return ok;
}

static bool run_body(Net& net, const float* encoded, float** out) {
    int width = net.inputEmbedding.inDim;
    float* tokens = nullptr;
    float* flow = nullptr;
    bool ok = alloc_float(net, &tokens, net.tokens * width);
    try {
        if (ok) {
            int total = net.tokens * width;
            bool peMap = net.peMap;
            int channels = net.inputChannels;
            int tokenCount = net.tokens;
            net.queue->parallel_for(sycl::range<1>(static_cast<size_t>(total)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                int token = i / width;
                int feature = i - token * width;
                if (feature < channels) tokens[i] = encoded[feature * tokenCount + token];
                else tokens[i] = (peMap && feature - channels == token) ? 1.0f : 0.0f;
            }).wait_and_throw();
        }
    } catch (const sycl::exception&) {
        ok = false;
    }
    if (ok) ok = run_dense_tokens(net, tokens, net.tokens, net.inputEmbedding, &flow);
    if (ok) ok = run_activate(net, flow, net.tokens * net.embedding, ACT_MISH);
    free_ptr(&net, tokens);
    for (const auto& block : net.encoders) {
        if (!ok) break;
        float* next = nullptr;
        ok = run_encoder_block(net, flow, block, net.tokens, net.eps, &next);
        free_ptr(&net, flow);
        flow = next;
    }
    if (ok) *out = flow;
    else free_ptr(&net, flow);
    return ok;
}

static bool run_policy(Net& net, const float* body, float** policyOut) {
    float* flow = nullptr;
    bool ok = run_dense_tokens(net, body, net.tokens, net.policy.embedding, &flow);
    if (ok) ok = run_activate(net, flow, net.tokens * net.policy.embedding.outDim, net.policy.activation);
    for (const auto& block : net.policy.encoders) {
        if (!ok) break;
        float* next = nullptr;
        ok = run_encoder_block(net, flow, block, net.tokens, net.eps, &next);
        free_ptr(&net, flow);
        flow = next;
    }
    float* q = nullptr;
    float* k = nullptr;
    float* internal = nullptr;
    float* policy = nullptr;
    if (ok) ok = run_dense_tokens(net, flow, net.tokens, net.policy.query, &q)
            && run_dense_tokens(net, flow, net.tokens, net.policy.key, &k)
            && alloc_float(net, &internal, INTERNAL_POLICY_SIZE)
            && alloc_float(net, &policy, net.policySize);
    try {
        if (ok) {
            net.queue->memset(internal, 0, INTERNAL_POLICY_SIZE * sizeof(float)).wait_and_throw();
            net.queue->memset(policy, 0, static_cast<size_t>(net.policySize) * sizeof(float)).wait_and_throw();
            int dModel = net.policy.query.outDim;
            const float* promotionWeights = net.policy.dPromotionWeights;
            const int* policyMap = net.dPolicyMap;
            net.queue->parallel_for(sycl::range<1>(64 * 64), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                int from = i / 64;
                int to = i - from * 64;
                float sum = 0.0f;
                for (int d = 0; d < dModel; ++d) sum += q[from * dModel + d] * k[to * dModel + d];
                internal[i] = sum * sycl::rsqrt(static_cast<float>(dModel));
            }).wait_and_throw();
            net.queue->parallel_for(sycl::range<1>(8 * 8 * 3), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                int promo = i % 3;
                int toFile = (i / 3) % 8;
                int fromFile = i / 24;
                if (toFile < fromFile - 1 || toFile > fromFile + 1) return;
                int from = 48 + fromFile;
                int to = 56 + toFile;
                float base = internal[from * 64 + to];
                float queen = 0.0f;
                float projected = 0.0f;
                for (int d = 0; d < dModel; ++d) {
                    queen += k[to * dModel + d] * promotionWeights[3 * dModel + d];
                    projected += k[to * dModel + d] * promotionWeights[promo * dModel + d];
                }
                internal[FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo] = base + queen + projected;
            }).wait_and_throw();
            net.queue->parallel_for(sycl::range<1>(INTERNAL_POLICY_SIZE), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                int mapped = policyMap[i];
                if (mapped >= 0) policy[mapped] = internal[i];
            }).wait_and_throw();
        }
    } catch (const sycl::exception&) {
        ok = false;
    }
    free_ptr(&net, flow);
    free_ptr(&net, q);
    free_ptr(&net, k);
    free_ptr(&net, internal);
    if (ok) *policyOut = policy;
    else free_ptr(&net, policy);
    return ok;
}

static bool run_value(Net& net, const float* body, float** wdlOut) {
    float* flow = nullptr;
    float* hidden = nullptr;
    float* logits = nullptr;
    bool ok = run_dense_tokens(net, body, net.tokens, net.value.embedding, &flow);
    if (ok) ok = run_activate(net, flow, net.tokens * net.value.embedding.outDim, net.value.activation);
    if (ok) ok = run_dense_vector(net, flow, net.value.fc1, &hidden);
    if (ok) ok = run_activate(net, hidden, net.value.fc1.outDim, net.value.activation);
    if (ok) ok = run_dense_vector(net, hidden, net.value.fc2, &logits);
    try {
        if (ok) {
            net.queue->single_task([=]() {
                float maxValue = sycl::fmax(logits[0], sycl::fmax(logits[1], logits[2]));
                float a = sycl::exp(logits[0] - maxValue);
                float b = sycl::exp(logits[1] - maxValue);
                float c = sycl::exp(logits[2] - maxValue);
                float sum = a + b + c;
                logits[0] = a / sum;
                logits[1] = b / sum;
                logits[2] = c / sum;
            }).wait_and_throw();
        }
    } catch (const sycl::exception&) {
        ok = false;
    }
    free_ptr(&net, flow);
    free_ptr(&net, hidden);
    if (ok) *wdlOut = logits;
    else free_ptr(&net, logits);
    return ok;
}

static bool predict(Net& net, const std::vector<float>& encoded, std::vector<float>& policy, std::vector<float>& wdl) {
    float* dEncoded = nullptr;
    float* body = nullptr;
    float* dPolicy = nullptr;
    float* dWdl = nullptr;
    bool ok = upload_floats(net, encoded, &dEncoded) && run_body(net, dEncoded, &body)
            && run_policy(net, body, &dPolicy) && run_value(net, body, &dWdl);
    if (ok) {
        try {
            policy.assign(static_cast<size_t>(net.policySize), 0.0f);
            wdl.assign(3, 0.0f);
            net.queue->memcpy(policy.data(), dPolicy, policy.size() * sizeof(float)).wait_and_throw();
            net.queue->memcpy(wdl.data(), dWdl, wdl.size() * sizeof(float)).wait_and_throw();
        } catch (const sycl::exception&) {
            ok = false;
        }
    }
    free_ptr(&net, dEncoded);
    free_ptr(&net, body);
    free_ptr(&net, dPolicy);
    free_ptr(&net, dWdl);
    return ok;
}

static std::string jstring_to_string(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_lc0_bt4_oneapi_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_nn_lc0_bt4_oneapi_Backend_nativeCreate(
        JNIEnv* env, jclass, jstring path) {
    std::string nativePath = jstring_to_string(env, path);
    if (nativePath.empty()) return 0L;
    return reinterpret_cast<jlong>(load_net(nativePath));
}

extern "C" JNIEXPORT void JNICALL Java_chess_nn_lc0_bt4_oneapi_Backend_nativeDestroy(
        JNIEnv*, jclass, jlong handle) {
    release_net(reinterpret_cast<Net*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL Java_chess_nn_lc0_bt4_oneapi_Backend_nativeGetName(
        JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net) return nullptr;
    return env->NewStringUTF(net->name.c_str());
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_nn_lc0_bt4_oneapi_Backend_nativeGetInfo(
        JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net) return nullptr;
    jlong values[7] = {
            static_cast<jlong>(net->inputChannels),
            static_cast<jlong>(net->tokens),
            static_cast<jlong>(net->embedding),
            static_cast<jlong>(net->encoderLayers),
            static_cast<jlong>(net->heads),
            static_cast<jlong>(net->policySize),
            static_cast<jlong>(net->parameterCount)
    };
    jlongArray out = env->NewLongArray(7);
    if (!out) return nullptr;
    env->SetLongArrayRegion(out, 0, 7, values);
    return out;
}

extern "C" JNIEXPORT jfloat JNICALL Java_chess_nn_lc0_bt4_oneapi_Backend_nativePredict(
        JNIEnv* env, jclass, jlong handle, jfloatArray encodedPlanes, jfloatArray outPolicy, jfloatArray outWdl) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net || !encodedPlanes || !outPolicy || !outWdl) return 0.0f;
    jsize encodedLength = env->GetArrayLength(encodedPlanes);
    if (encodedLength != net->inputChannels * net->tokens
            || env->GetArrayLength(outPolicy) != net->policySize
            || env->GetArrayLength(outWdl) != 3) {
        return 0.0f;
    }
    std::vector<float> encoded(static_cast<size_t>(encodedLength));
    env->GetFloatArrayRegion(encodedPlanes, 0, encodedLength, encoded.data());
    std::vector<float> policy;
    std::vector<float> wdl;
    if (!predict(*net, encoded, policy, wdl)) return 0.0f;
    env->SetFloatArrayRegion(outPolicy, 0, net->policySize, policy.data());
    env->SetFloatArrayRegion(outWdl, 0, 3, wdl.data());
    return wdl[0] - wdl[2];
}
