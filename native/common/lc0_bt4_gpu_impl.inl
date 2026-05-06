#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <limits>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#define BT4_CAT2(a, b) a##b
#define BT4_CAT(a, b) BT4_CAT2(a, b)
#define BT4_JNI(name) BT4_CAT(BT4_JNI_PREFIX, name)

namespace {

constexpr int BT4_MAGIC = 0x4A345442;
constexpr int BT4_VERSION = 1;
constexpr int BT4_TOKENS = 64;
constexpr int BT4_INPUT_CHANNELS = 112;
constexpr int BT4_POLICY_SIZE = 1858;
constexpr int BT4_INTERNAL_POLICY_SIZE = 67 * 64;
constexpr int BT4_FROM_TO_POLICY_SIZE = 64 * 64;

enum Bt4Activation {
    BT4_ACT_NONE = 0,
    BT4_ACT_RELU = 1,
    BT4_ACT_MISH = 2
};

struct Bt4Dense {
    int inDim = 0;
    int outDim = 0;
    std::vector<float> weights;
    std::vector<float> bias;
    float* dWeights = nullptr;
    float* dBias = nullptr;
};

struct Bt4Attention {
    int heads = 0;
    Bt4Dense query;
    Bt4Dense key;
    Bt4Dense value;
    Bt4Dense out;
};

struct Bt4EncoderBlock {
    Bt4Attention attention;
    Bt4Dense ffnIn;
    Bt4Dense ffnOut;
    std::vector<float> ln1Gamma;
    std::vector<float> ln1Beta;
    std::vector<float> ln2Gamma;
    std::vector<float> ln2Beta;
    float* dLn1Gamma = nullptr;
    float* dLn1Beta = nullptr;
    float* dLn2Gamma = nullptr;
    float* dLn2Beta = nullptr;
    int activation = BT4_ACT_MISH;
    float alpha = 1.0f;
};

struct Bt4PolicyHead {
    Bt4Dense embedding;
    std::vector<Bt4EncoderBlock> encoders;
    Bt4Dense query;
    Bt4Dense key;
    std::vector<float> promotionWeights;
    float* dPromotionWeights = nullptr;
    int activation = BT4_ACT_MISH;
};

struct Bt4ValueHead {
    Bt4Dense embedding;
    Bt4Dense fc1;
    Bt4Dense fc2;
    int activation = BT4_ACT_MISH;
};

struct Bt4Net {
    std::string name;
    bool peMap = true;
    int inputChannels = BT4_INPUT_CHANNELS;
    int tokens = BT4_TOKENS;
    int embedding = 0;
    int encoderLayers = 0;
    int heads = 0;
    int policySize = BT4_POLICY_SIZE;
    float eps = 1.0e-6f;
    long long parameterCount = 0;
    Bt4Dense inputEmbedding;
    std::vector<Bt4EncoderBlock> encoders;
    Bt4PolicyHead policy;
    Bt4ValueHead value;
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
        require(4);
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
        static_assert(sizeof(out) == sizeof(bits), "float32 expected");
        std::memcpy(&out, &bits, sizeof(out));
        return out;
    }

    std::string str() {
        int32_t n = i32();
        if (n < 0 || n > 1000000) throw std::runtime_error("bad string length");
        require(static_cast<size_t>(n));
        std::string out(data.data() + pos, data.data() + pos + n);
        pos += static_cast<size_t>(n);
        return out;
    }

    std::vector<float> floats() {
        int32_t n = i32();
        if (n < 0) throw std::runtime_error("bad float count");
        std::vector<float> out(static_cast<size_t>(n));
        for (int32_t i = 0; i < n; ++i) out[static_cast<size_t>(i)] = f32();
        return out;
    }

    bool done() const {
        return pos == data.size();
    }

private:
    std::vector<char> data;
    size_t pos = 0;

    void require(size_t n) {
        if (pos + n > data.size()) throw std::runtime_error("unexpected eof");
    }
};

static int parse_activation(const std::string& s) {
    if (s == "NONE") return BT4_ACT_NONE;
    if (s == "RELU") return BT4_ACT_RELU;
    if (s == "MISH") return BT4_ACT_MISH;
    throw std::runtime_error("bad activation");
}

static bool gpu_ok(decltype(BT4_GPU_LAST_ERROR()) err) {
    return err == BT4_GPU_SUCCESS;
}

static bool upload_floats(const std::vector<float>& host, float** device) {
    *device = nullptr;
    if (host.empty()) return true;
    size_t bytes = host.size() * sizeof(float);
    if (!gpu_ok(BT4_GPU_MALLOC(device, bytes))) return false;
    if (!gpu_ok(BT4_GPU_MEMCPY(*device, host.data(), bytes, BT4_GPU_MEMCPY_H2D))) return false;
    return true;
}

static bool upload_ints(const std::vector<int>& host, int** device) {
    *device = nullptr;
    if (host.empty()) return true;
    size_t bytes = host.size() * sizeof(int);
    if (!gpu_ok(BT4_GPU_MALLOC(device, bytes))) return false;
    if (!gpu_ok(BT4_GPU_MEMCPY(*device, host.data(), bytes, BT4_GPU_MEMCPY_H2D))) return false;
    return true;
}

static void release_dense(Bt4Dense& dense) {
    if (dense.dWeights) BT4_GPU_FREE(dense.dWeights);
    if (dense.dBias) BT4_GPU_FREE(dense.dBias);
    dense.dWeights = nullptr;
    dense.dBias = nullptr;
}

static void release_block(Bt4EncoderBlock& block);

static bool upload_dense(Bt4Dense& dense) {
    return upload_floats(dense.weights, &dense.dWeights) && upload_floats(dense.bias, &dense.dBias);
}

static bool upload_attention(Bt4Attention& attention) {
    return upload_dense(attention.query) && upload_dense(attention.key)
            && upload_dense(attention.value) && upload_dense(attention.out);
}

static void release_attention(Bt4Attention& attention) {
    release_dense(attention.query);
    release_dense(attention.key);
    release_dense(attention.value);
    release_dense(attention.out);
}

static bool upload_block(Bt4EncoderBlock& block) {
    return upload_attention(block.attention)
            && upload_dense(block.ffnIn)
            && upload_dense(block.ffnOut)
            && upload_floats(block.ln1Gamma, &block.dLn1Gamma)
            && upload_floats(block.ln1Beta, &block.dLn1Beta)
            && upload_floats(block.ln2Gamma, &block.dLn2Gamma)
            && upload_floats(block.ln2Beta, &block.dLn2Beta);
}

static void release_block(Bt4EncoderBlock& block) {
    release_attention(block.attention);
    release_dense(block.ffnIn);
    release_dense(block.ffnOut);
    if (block.dLn1Gamma) BT4_GPU_FREE(block.dLn1Gamma);
    if (block.dLn1Beta) BT4_GPU_FREE(block.dLn1Beta);
    if (block.dLn2Gamma) BT4_GPU_FREE(block.dLn2Gamma);
    if (block.dLn2Beta) BT4_GPU_FREE(block.dLn2Beta);
    block.dLn1Gamma = nullptr;
    block.dLn1Beta = nullptr;
    block.dLn2Gamma = nullptr;
    block.dLn2Beta = nullptr;
}

static void release_net(Bt4Net* net) {
    if (!net) return;
    release_dense(net->inputEmbedding);
    for (auto& block : net->encoders) release_block(block);
    release_dense(net->policy.embedding);
    for (auto& block : net->policy.encoders) release_block(block);
    release_dense(net->policy.query);
    release_dense(net->policy.key);
    if (net->policy.dPromotionWeights) BT4_GPU_FREE(net->policy.dPromotionWeights);
    release_dense(net->value.embedding);
    release_dense(net->value.fc1);
    release_dense(net->value.fc2);
    if (net->dPolicyMap) BT4_GPU_FREE(net->dPolicyMap);
    delete net;
}

static Bt4Dense read_dense(Reader& in) {
    Bt4Dense dense;
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

static Bt4Attention read_attention(Reader& in) {
    Bt4Attention attention;
    attention.heads = in.i32();
    attention.query = read_dense(in);
    attention.key = read_dense(in);
    attention.value = read_dense(in);
    attention.out = read_dense(in);
    return attention;
}

static Bt4EncoderBlock read_block(Reader& in) {
    Bt4EncoderBlock block;
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

static std::vector<Bt4EncoderBlock> read_blocks(Reader& in) {
    int count = in.i32();
    if (count < 0) throw std::runtime_error("bad block count");
    std::vector<Bt4EncoderBlock> blocks;
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
    std::vector<int> map(BT4_INTERNAL_POLICY_SIZE, -1);
    int next = 0;
    for (int from = 0; from < 64; ++from) {
        for (int to = 0; to < 64; ++to) {
            if (from != to && queen_like_or_knight(from, to)) {
                map[static_cast<size_t>(from * 64 + to)] = next++;
            }
        }
    }
    for (int fromFile = 0; fromFile < 8; ++fromFile) {
        int minTo = std::max(0, fromFile - 1);
        int maxTo = std::min(7, fromFile + 1);
        for (int toFile = minTo; toFile <= maxTo; ++toFile) {
            for (int promo = 0; promo < 3; ++promo) {
                map[static_cast<size_t>(BT4_FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo)] = next++;
            }
        }
    }
    if (next != BT4_POLICY_SIZE) throw std::runtime_error("bad policy map");
    return map;
}

static long long dense_params(const Bt4Dense& dense) {
    return static_cast<long long>(dense.weights.size() + dense.bias.size());
}

static long long attention_params(const Bt4Attention& attention) {
    return dense_params(attention.query) + dense_params(attention.key)
            + dense_params(attention.value) + dense_params(attention.out);
}

static long long block_params(const Bt4EncoderBlock& block) {
    return attention_params(block.attention) + dense_params(block.ffnIn) + dense_params(block.ffnOut)
            + static_cast<long long>(block.ln1Gamma.size() + block.ln1Beta.size()
                    + block.ln2Gamma.size() + block.ln2Beta.size());
}

static long long compute_params(const Bt4Net& net) {
    long long total = dense_params(net.inputEmbedding);
    for (const auto& block : net.encoders) total += block_params(block);
    total += dense_params(net.policy.embedding) + dense_params(net.policy.query) + dense_params(net.policy.key)
            + static_cast<long long>(net.policy.promotionWeights.size());
    for (const auto& block : net.policy.encoders) total += block_params(block);
    total += dense_params(net.value.embedding) + dense_params(net.value.fc1) + dense_params(net.value.fc2);
    return total;
}

static bool upload_net(Bt4Net& net) {
    if (!upload_dense(net.inputEmbedding)) return false;
    for (auto& block : net.encoders) if (!upload_block(block)) return false;
    if (!upload_dense(net.policy.embedding)) return false;
    for (auto& block : net.policy.encoders) if (!upload_block(block)) return false;
    if (!upload_dense(net.policy.query) || !upload_dense(net.policy.key)) return false;
    if (!upload_floats(net.policy.promotionWeights, &net.policy.dPromotionWeights)) return false;
    if (!upload_dense(net.value.embedding) || !upload_dense(net.value.fc1) || !upload_dense(net.value.fc2)) return false;
    if (!upload_ints(net.policyMap, &net.dPolicyMap)) return false;
    return true;
}

static Bt4Net* load_net(const std::string& path) {
    auto* net = new Bt4Net();
    try {
        Reader in(path);
        if (in.i32() != BT4_MAGIC || in.i32() != BT4_VERSION) throw std::runtime_error("bad header");
        net->name = in.str();
        (void) in.str(); // input format
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
        if (net->tokens != BT4_TOKENS || net->inputChannels != BT4_INPUT_CHANNELS
                || net->policySize != BT4_POLICY_SIZE) {
            throw std::runtime_error("unsupported BT4 shape");
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

__device__ float bt4_activate(float x, int activation) {
    if (activation == BT4_ACT_RELU) return x > 0.0f ? x : 0.0f;
    if (activation == BT4_ACT_MISH) {
        float softplus;
        if (x > 20.0f) softplus = x;
        else if (x < -20.0f) softplus = expf(x);
        else softplus = log1pf(expf(x));
        return x * tanhf(softplus);
    }
    return x;
}

__global__ void planes_to_tokens_kernel(const float* planes, float* out, int channels, int tokens, int width, int peMap) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    int total = tokens * width;
    if (i >= total) return;
    int token = i / width;
    int feature = i - token * width;
    if (feature < channels) {
        out[i] = planes[feature * tokens + token];
    } else {
        out[i] = (peMap && feature - channels == token) ? 1.0f : 0.0f;
    }
}

__global__ void dense_tokens_kernel(
        const float* input, int rows, int inDim, const float* weights, const float* bias, int outDim, float* out) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    int total = rows * outDim;
    if (i >= total) return;
    int row = i / outDim;
    int col = i - row * outDim;
    float sum = bias[col];
    const float* in = input + row * inDim;
    const float* w = weights + col * inDim;
    for (int k = 0; k < inDim; ++k) sum += w[k] * in[k];
    out[i] = sum;
}

__global__ void dense_vector_kernel(
        const float* input, int inDim, const float* weights, const float* bias, int outDim, float* out) {
    int col = blockIdx.x * blockDim.x + threadIdx.x;
    if (col >= outDim) return;
    float sum = bias[col];
    const float* w = weights + col * inDim;
    for (int k = 0; k < inDim; ++k) sum += w[k] * input[k];
    out[col] = sum;
}

__global__ void activate_kernel(float* values, int n, int activation) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) values[i] = bt4_activate(values[i], activation);
}

__global__ void attention_kernel(
        const float* q, const float* k, const float* v, float* out,
        int tokens, int dModel, int heads) {
    int depth = dModel / heads;
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    int total = tokens * heads * depth;
    if (i >= total) return;
    int d = i % depth;
    int head = (i / depth) % heads;
    int queryToken = i / (depth * heads);
    int headOffset = head * depth;
    float invScale = rsqrtf(static_cast<float>(depth));
    float maxScore = -3.402823466e+38F;
    for (int keyToken = 0; keyToken < tokens; ++keyToken) {
        float sum = 0.0f;
        int qb = queryToken * dModel + headOffset;
        int kb = keyToken * dModel + headOffset;
        for (int x = 0; x < depth; ++x) sum += q[qb + x] * k[kb + x];
        float score = sum * invScale;
        if (score > maxScore) maxScore = score;
    }
    float denom = 0.0f;
    float weighted = 0.0f;
    for (int keyToken = 0; keyToken < tokens; ++keyToken) {
        float sum = 0.0f;
        int qb = queryToken * dModel + headOffset;
        int kb = keyToken * dModel + headOffset;
        for (int x = 0; x < depth; ++x) sum += q[qb + x] * k[kb + x];
        float e = expf(sum * invScale - maxScore);
        denom += e;
        weighted += e * v[keyToken * dModel + headOffset + d];
    }
    out[queryToken * dModel + headOffset + d] = denom > 0.0f ? weighted / denom : 0.0f;
}

__global__ void add_residual_kernel(float* dest, const float* residual, int n, float scale) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) dest[i] = dest[i] * scale + residual[i];
}

__global__ void layernorm_kernel(float* values, int rows, int dim, const float* gamma, const float* beta, float eps) {
    int row = blockIdx.x * blockDim.x + threadIdx.x;
    if (row >= rows) return;
    float* base = values + row * dim;
    float mean = 0.0f;
    for (int i = 0; i < dim; ++i) mean += base[i];
    mean /= static_cast<float>(dim);
    float variance = 0.0f;
    for (int i = 0; i < dim; ++i) {
        float centered = base[i] - mean;
        variance += centered * centered;
    }
    float invStd = rsqrtf(variance / static_cast<float>(dim) + eps);
    for (int i = 0; i < dim; ++i) base[i] = (base[i] - mean) * invStd * gamma[i] + beta[i];
}

__global__ void policy_from_to_kernel(const float* q, const float* k, float* internal, int dModel) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= 64 * 64) return;
    int from = i / 64;
    int to = i - from * 64;
    float sum = 0.0f;
    const float* qb = q + from * dModel;
    const float* kb = k + to * dModel;
    for (int d = 0; d < dModel; ++d) sum += qb[d] * kb[d];
    internal[i] = sum * rsqrtf(static_cast<float>(dModel));
}

__global__ void underpromotion_kernel(float* internal, const float* key, int dModel, const float* promotionWeights) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= 8 * 8 * 3) return;
    int promo = i % 3;
    int toFile = (i / 3) % 8;
    int fromFile = i / 24;
    if (toFile < fromFile - 1 || toFile > fromFile + 1) return;
    int from = 48 + fromFile;
    int to = 56 + toFile;
    float base = internal[from * 64 + to];
    float queen = 0.0f;
    float projected = 0.0f;
    const float* kb = key + to * dModel;
    const float* qw = promotionWeights + 3 * dModel;
    const float* pw = promotionWeights + promo * dModel;
    for (int d = 0; d < dModel; ++d) {
        queen += kb[d] * qw[d];
        projected += kb[d] * pw[d];
    }
    int internalIndex = BT4_FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo;
    internal[internalIndex] = base + queen + projected;
}

__global__ void gather_policy_kernel(const float* internal, const int* map, float* policy) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= BT4_INTERNAL_POLICY_SIZE) return;
    int mapped = map[i];
    if (mapped >= 0) policy[mapped] = internal[i];
}

__global__ void softmax3_kernel(float* logits) {
    float maxValue = fmaxf(logits[0], fmaxf(logits[1], logits[2]));
    float a = expf(logits[0] - maxValue);
    float b = expf(logits[1] - maxValue);
    float c = expf(logits[2] - maxValue);
    float sum = a + b + c;
    logits[0] = a / sum;
    logits[1] = b / sum;
    logits[2] = c / sum;
}

static bool check_launch() {
    return gpu_ok(BT4_GPU_LAST_ERROR());
}

static bool alloc_float(float** ptr, int count) {
    *ptr = nullptr;
    return count >= 0 && gpu_ok(BT4_GPU_MALLOC(ptr, static_cast<size_t>(count) * sizeof(float)));
}

static bool run_activate(float* data, int n, int activation) {
    if (activation == BT4_ACT_NONE) return true;
    int block = 256;
    int grid = (n + block - 1) / block;
    activate_kernel<<<grid, block>>>(data, n, activation);
    return check_launch();
}

static bool run_dense_tokens(const float* input, int rows, const Bt4Dense& dense, float** out) {
    int total = rows * dense.outDim;
    if (!alloc_float(out, total)) return false;
    int block = 256;
    int grid = (total + block - 1) / block;
    dense_tokens_kernel<<<grid, block>>>(input, rows, dense.inDim, dense.dWeights, dense.dBias, dense.outDim, *out);
    return check_launch();
}

static bool run_dense_vector(const float* input, const Bt4Dense& dense, float** out) {
    if (!alloc_float(out, dense.outDim)) return false;
    int block = 256;
    int grid = (dense.outDim + block - 1) / block;
    dense_vector_kernel<<<grid, block>>>(input, dense.inDim, dense.dWeights, dense.dBias, dense.outDim, *out);
    return check_launch();
}

static bool run_attention(const float* input, const Bt4EncoderBlock& block, int tokens, float** out) {
    float* q = nullptr;
    float* k = nullptr;
    float* v = nullptr;
    float* combined = nullptr;
    bool ok = run_dense_tokens(input, tokens, block.attention.query, &q)
            && run_dense_tokens(input, tokens, block.attention.key, &k)
            && run_dense_tokens(input, tokens, block.attention.value, &v)
            && alloc_float(&combined, tokens * block.attention.query.outDim);
    if (ok) {
        int total = tokens * block.attention.query.outDim;
        int blockSize = 256;
        int grid = (total + blockSize - 1) / blockSize;
        attention_kernel<<<grid, blockSize>>>(q, k, v, combined, tokens, block.attention.query.outDim,
                block.attention.heads);
        ok = check_launch() && run_dense_tokens(combined, tokens, block.attention.out, out);
    }
    if (q) BT4_GPU_FREE(q);
    if (k) BT4_GPU_FREE(k);
    if (v) BT4_GPU_FREE(v);
    if (combined) BT4_GPU_FREE(combined);
    return ok;
}

static bool run_encoder_block(float* input, const Bt4EncoderBlock& block, int tokens, float eps, float** out) {
    int embedding = block.attention.out.outDim;
    int elements = tokens * embedding;
    float* attended = nullptr;
    float* hidden = nullptr;
    float* ffnOut = nullptr;
    bool ok = run_attention(input, block, tokens, &attended);
    if (ok) {
        int grid = (elements + 255) / 256;
        add_residual_kernel<<<grid, 256>>>(attended, input, elements, block.alpha);
        ok = check_launch();
    }
    if (ok) {
        int grid = (tokens + 127) / 128;
        layernorm_kernel<<<grid, 128>>>(attended, tokens, embedding, block.dLn1Gamma, block.dLn1Beta, eps);
        ok = check_launch();
    }
    if (ok) ok = run_dense_tokens(attended, tokens, block.ffnIn, &hidden);
    if (ok) ok = run_activate(hidden, tokens * block.ffnIn.outDim, block.activation);
    if (ok) ok = run_dense_tokens(hidden, tokens, block.ffnOut, &ffnOut);
    if (ok) {
        int grid = (elements + 255) / 256;
        add_residual_kernel<<<grid, 256>>>(ffnOut, attended, elements, block.alpha);
        ok = check_launch();
    }
    if (ok) {
        int grid = (tokens + 127) / 128;
        layernorm_kernel<<<grid, 128>>>(ffnOut, tokens, embedding, block.dLn2Gamma, block.dLn2Beta, eps);
        ok = check_launch();
    }
    if (attended) BT4_GPU_FREE(attended);
    if (hidden) BT4_GPU_FREE(hidden);
    if (ok) {
        *out = ffnOut;
    } else if (ffnOut) {
        BT4_GPU_FREE(ffnOut);
    }
    return ok;
}

static bool run_body(const Bt4Net& net, const float* encoded, float** out) {
    int width = net.inputEmbedding.inDim;
    float* tokens = nullptr;
    float* flow = nullptr;
    if (!alloc_float(&tokens, net.tokens * width)) return false;
    int block = 256;
    int total = net.tokens * width;
    int grid = (total + block - 1) / block;
    planes_to_tokens_kernel<<<grid, block>>>(encoded, tokens, net.inputChannels, net.tokens, width, net.peMap ? 1 : 0);
    bool ok = check_launch() && run_dense_tokens(tokens, net.tokens, net.inputEmbedding, &flow);
    if (ok) ok = run_activate(flow, net.tokens * net.embedding, BT4_ACT_MISH);
    if (tokens) BT4_GPU_FREE(tokens);
    for (const auto& blockWeights : net.encoders) {
        if (!ok) break;
        float* next = nullptr;
        ok = run_encoder_block(flow, blockWeights, net.tokens, net.eps, &next);
        BT4_GPU_FREE(flow);
        flow = next;
    }
    if (ok) {
        *out = flow;
    } else if (flow) {
        BT4_GPU_FREE(flow);
    }
    return ok;
}

static bool run_policy(const Bt4Net& net, const float* body, float** policyOut) {
    float* flow = nullptr;
    bool ok = run_dense_tokens(body, net.tokens, net.policy.embedding, &flow);
    if (ok) ok = run_activate(flow, net.tokens * net.policy.embedding.outDim, net.policy.activation);
    for (const auto& blockWeights : net.policy.encoders) {
        if (!ok) break;
        float* next = nullptr;
        ok = run_encoder_block(flow, blockWeights, net.tokens, net.eps, &next);
        BT4_GPU_FREE(flow);
        flow = next;
    }
    float* q = nullptr;
    float* k = nullptr;
    float* internal = nullptr;
    float* policy = nullptr;
    if (ok) ok = run_dense_tokens(flow, net.tokens, net.policy.query, &q)
            && run_dense_tokens(flow, net.tokens, net.policy.key, &k)
            && alloc_float(&internal, BT4_INTERNAL_POLICY_SIZE)
            && alloc_float(&policy, net.policySize);
    if (ok) {
        BT4_GPU_MEMSET(internal, 0, BT4_INTERNAL_POLICY_SIZE * sizeof(float));
        BT4_GPU_MEMSET(policy, 0, static_cast<size_t>(net.policySize) * sizeof(float));
        int grid = (64 * 64 + 255) / 256;
        policy_from_to_kernel<<<grid, 256>>>(q, k, internal, net.policy.query.outDim);
        ok = check_launch();
    }
    if (ok) {
        underpromotion_kernel<<<1, 256>>>(internal, k, net.policy.query.outDim, net.policy.dPromotionWeights);
        ok = check_launch();
    }
    if (ok) {
        int grid = (BT4_INTERNAL_POLICY_SIZE + 255) / 256;
        gather_policy_kernel<<<grid, 256>>>(internal, net.dPolicyMap, policy);
        ok = check_launch();
    }
    if (flow) BT4_GPU_FREE(flow);
    if (q) BT4_GPU_FREE(q);
    if (k) BT4_GPU_FREE(k);
    if (internal) BT4_GPU_FREE(internal);
    if (ok) {
        *policyOut = policy;
    } else if (policy) {
        BT4_GPU_FREE(policy);
    }
    return ok;
}

static bool run_value(const Bt4Net& net, const float* body, float** wdlOut) {
    float* flow = nullptr;
    float* hidden = nullptr;
    float* logits = nullptr;
    bool ok = run_dense_tokens(body, net.tokens, net.value.embedding, &flow);
    if (ok) ok = run_activate(flow, net.tokens * net.value.embedding.outDim, net.value.activation);
    if (ok) ok = run_dense_vector(flow, net.value.fc1, &hidden);
    if (ok) ok = run_activate(hidden, net.value.fc1.outDim, net.value.activation);
    if (ok) ok = run_dense_vector(hidden, net.value.fc2, &logits);
    if (ok) {
        softmax3_kernel<<<1, 1>>>(logits);
        ok = check_launch();
    }
    if (flow) BT4_GPU_FREE(flow);
    if (hidden) BT4_GPU_FREE(hidden);
    if (ok) {
        *wdlOut = logits;
    } else if (logits) {
        BT4_GPU_FREE(logits);
    }
    return ok;
}

static bool predict_gpu(const Bt4Net& net, const std::vector<float>& encoded, std::vector<float>& policy,
        std::vector<float>& wdl) {
    float* dEncoded = nullptr;
    float* body = nullptr;
    float* dPolicy = nullptr;
    float* dWdl = nullptr;
    bool ok = upload_floats(encoded, &dEncoded)
            && run_body(net, dEncoded, &body)
            && run_policy(net, body, &dPolicy)
            && run_value(net, body, &dWdl);
    if (ok) {
        ok = gpu_ok(BT4_GPU_DEVICE_SYNCHRONIZE());
    }
    if (ok) {
        policy.assign(static_cast<size_t>(net.policySize), 0.0f);
        wdl.assign(3, 0.0f);
        ok = gpu_ok(BT4_GPU_MEMCPY(policy.data(), dPolicy, policy.size() * sizeof(float), BT4_GPU_MEMCPY_D2H))
                && gpu_ok(BT4_GPU_MEMCPY(wdl.data(), dWdl, wdl.size() * sizeof(float), BT4_GPU_MEMCPY_D2H));
    }
    if (dEncoded) BT4_GPU_FREE(dEncoded);
    if (body) BT4_GPU_FREE(body);
    if (dPolicy) BT4_GPU_FREE(dPolicy);
    if (dWdl) BT4_GPU_FREE(dWdl);
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

extern "C" JNIEXPORT jint JNICALL BT4_JNI(Support_nativeDeviceCount)(JNIEnv*, jclass) {
    int count = 0;
    if (!gpu_ok(BT4_GPU_GET_DEVICE_COUNT(&count))) return 0;
    return count;
}

extern "C" JNIEXPORT jlong JNICALL BT4_JNI(Backend_nativeCreate)(JNIEnv* env, jclass, jstring path) {
    std::string nativePath = jstring_to_string(env, path);
    if (nativePath.empty()) return 0L;
    return reinterpret_cast<jlong>(load_net(nativePath));
}

extern "C" JNIEXPORT void JNICALL BT4_JNI(Backend_nativeDestroy)(JNIEnv*, jclass, jlong handle) {
    release_net(reinterpret_cast<Bt4Net*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL BT4_JNI(Backend_nativeGetName)(JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Bt4Net*>(handle);
    if (!net) return nullptr;
    return env->NewStringUTF(net->name.c_str());
}

extern "C" JNIEXPORT jlongArray JNICALL BT4_JNI(Backend_nativeGetInfo)(JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Bt4Net*>(handle);
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

extern "C" JNIEXPORT jfloat JNICALL BT4_JNI(Backend_nativePredict)(
        JNIEnv* env, jclass, jlong handle, jfloatArray encodedPlanes, jfloatArray outPolicy, jfloatArray outWdl) {
    auto* net = reinterpret_cast<Bt4Net*>(handle);
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
    if (!predict_gpu(*net, encoded, policy, wdl)) {
        return 0.0f;
    }
    env->SetFloatArrayRegion(outPolicy, 0, net->policySize, policy.data());
    env->SetFloatArrayRegion(outWdl, 0, 3, wdl.data());
    return wdl[0] - wdl[2];
}

#undef BT4_JNI
#undef BT4_CAT
#undef BT4_CAT2
