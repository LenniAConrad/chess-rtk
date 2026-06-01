/*
 * native/oneapi/otis_oneapi_jni.cpp
 *
 * Optional oneAPI/SYCL backend for OTIS policy/WDL inference.
 *
 * Mirrors the CUDA/ROCm implementation in native/common/otis_gpu_impl.inl: the
 * board is reconstructed from the encoded simple_18 planes so the typed tactical
 * relation masks can be rebuilt on the host and uploaded, and the OTIS forward
 * pass (square tokens -> typed sheaf trunk -> readout -> policy/WDL heads) runs
 * in SYCL kernels. The per-legal-move policy refinement applied by the Java CPU
 * path is intentionally not reproduced (no legal-move generator), so the
 * returned policy is the raw policy-head logits.
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

constexpr int OTIS_MAGIC = 0x5349544F;
constexpr int OTIS_VERSION = 2;
constexpr int SQUARES = 64;
constexpr int INPUT_PLANES = 18;
constexpr int PIECE_STATE_PLANES = 13;
constexpr int RAW_DIM = 32;
constexpr int PIECE_DIM = 16;
constexpr int COORD_DIM = 8;
constexpr int COORD_IN = 6;
constexpr int FUSE_DIM = RAW_DIM + PIECE_DIM + COORD_DIM;
constexpr int HIDDEN_DIM = 96;
constexpr int TRIAD_DIM = 4;
constexpr int BOARD_STATS_DIM = 8;
constexpr int RELATION_COUNT = 12;
constexpr int STALK_DIM = 8;
constexpr int WDL_OUTPUTS = 3;
constexpr int MAX_CHANNELS = 512;
constexpr float SHEAF_ETA = 0.125f;

constexpr int P_EMPTY = 0;
constexpr int P_PAWN = 1;
constexpr int P_KNIGHT = 2;
constexpr int P_BISHOP = 3;
constexpr int P_ROOK = 4;
constexpr int P_QUEEN = 5;
constexpr int P_KING = 6;

static int readout_dim(int channels) {
    return channels * 4 + RELATION_COUNT * 4 + TRIAD_DIM + BOARD_STATS_DIM;
}

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

struct Net {
    std::unique_ptr<sycl::queue> queue;
    std::string name;
    int inputPlanes = INPUT_PLANES;
    int channels = 0;
    int blocks = 0;
    int policySize = 0;
    long long parameterCount = 0;

    float* rawProjW = nullptr;
    float* rawProjB = nullptr;
    float* pieceProjW = nullptr;
    float* pieceProjB = nullptr;
    float* coordProjW = nullptr;
    float* coordProjB = nullptr;
    float* fuseInW = nullptr;
    float* fuseInB = nullptr;
    float* fuseNormW = nullptr;
    float* fuseNormB = nullptr;
    float* fuseOutW = nullptr;
    float* fuseOutB = nullptr;
    float* encNormW = nullptr;
    float* encNormB = nullptr;
    float* rhoSrc = nullptr;
    float* rhoDst = nullptr;
    float* relGate = nullptr;
    float* etaLogits = nullptr;
    float* n2sW = nullptr;
    float* n2sB = nullptr;
    float* s2nW = nullptr;
    float* s2nB = nullptr;
    float* mlpNormW = nullptr;
    float* mlpNormB = nullptr;
    float* mlpUpW = nullptr;
    float* mlpUpB = nullptr;
    float* mlpDownW = nullptr;
    float* mlpDownB = nullptr;
    float* blockNormW = nullptr;
    float* blockNormB = nullptr;
    float* triadAW = nullptr;
    float* triadTW = nullptr;
    float* triadDW = nullptr;
    float* triadNormW = nullptr;
    float* triadNormB = nullptr;
    float* readNormW = nullptr;
    float* readNormB = nullptr;
    float* readHidW = nullptr;
    float* readHidB = nullptr;
    float* policyW = nullptr;
    float* policyB = nullptr;
    float* wdlW = nullptr;
    float* wdlB = nullptr;
    float* squareAtlas = nullptr;
    float* policyAtlas = nullptr;
    float* valueAtlas = nullptr;

    float* dInput = nullptr;
    int* dBoard = nullptr;
    float* dMasks = nullptr;
    float* tokens = nullptr;
    float* tokTmp = nullptr;
    float* salience = nullptr;
    float* h = nullptr;
    float* stalks = nullptr;
    float* stalkDelta = nullptr;
    float* update = nullptr;
    float* degree = nullptr;
    float* node = nullptr;
    float* laplacian = nullptr;
    float* energy = nullptr;
    float* gates = nullptr;
    float* density = nullptr;
    float* srcPressure = nullptr;
    float* dstPressure = nullptr;
    float* scalars = nullptr;
    float* trunk = nullptr;
    float* hidden = nullptr;
    float* policy = nullptr;
    float* wdlLogits = nullptr;
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
        int32_t n = i32();
        if (n < 0 || n > 4096) throw std::runtime_error("bad string");
        need(static_cast<size_t>(n));
        std::string out(data.data() + pos, data.data() + pos + n);
        pos += static_cast<size_t>(n);
        return out;
    }

    std::vector<float> floats(int expected) {
        int32_t n = i32();
        if (n != expected) throw std::runtime_error("bad tensor length");
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

    void need(size_t n) {
        if (pos + n > data.size()) throw std::runtime_error("eof");
    }
};

static bool upload(Net& net, const std::vector<float>& host, float** device, long long& params) {
    *device = nullptr;
    params += static_cast<long long>(host.size());
    if (host.empty()) return true;
    try {
        *device = sycl::malloc_device<float>(host.size(), *net.queue);
        net.queue->memcpy(*device, host.data(), host.size() * sizeof(float)).wait_and_throw();
        return *device != nullptr;
    } catch (const sycl::exception&) {
        return false;
    }
}

static void free_net(Net* net) {
    if (!net) return;
    if (net->queue) {
        auto freep = [&](void* p) { if (p) sycl::free(p, *net->queue); };
        void* buffers[] = {
            net->rawProjW, net->rawProjB, net->pieceProjW, net->pieceProjB, net->coordProjW, net->coordProjB,
            net->fuseInW, net->fuseInB, net->fuseNormW, net->fuseNormB, net->fuseOutW, net->fuseOutB,
            net->encNormW, net->encNormB, net->rhoSrc, net->rhoDst, net->relGate, net->etaLogits,
            net->n2sW, net->n2sB, net->s2nW, net->s2nB, net->mlpNormW, net->mlpNormB,
            net->mlpUpW, net->mlpUpB, net->mlpDownW, net->mlpDownB, net->blockNormW, net->blockNormB,
            net->triadAW, net->triadTW, net->triadDW, net->triadNormW, net->triadNormB,
            net->readNormW, net->readNormB, net->readHidW, net->readHidB, net->policyW, net->policyB,
            net->wdlW, net->wdlB, net->squareAtlas, net->policyAtlas, net->valueAtlas,
            net->dInput, net->dBoard, net->dMasks, net->tokens, net->tokTmp, net->salience, net->h,
            net->stalks, net->stalkDelta, net->update, net->degree, net->node, net->laplacian,
            net->energy, net->gates, net->density, net->srcPressure, net->dstPressure, net->scalars,
            net->trunk, net->hidden, net->policy, net->wdlLogits
        };
        for (void* p : buffers) freep(p);
    }
    delete net;
}

static bool alloc_scratch(Net& net) {
    int c = net.channels;
    auto allocf = [&](float** ptr, size_t count) -> bool {
        try {
            *ptr = sycl::malloc_device<float>(count, *net.queue);
            return *ptr != nullptr;
        } catch (const sycl::exception&) {
            return false;
        }
    };
    bool ok =
        allocf(&net.dInput, static_cast<size_t>(INPUT_PLANES) * SQUARES)
        && allocf(&net.dMasks, static_cast<size_t>(RELATION_COUNT) * SQUARES * SQUARES)
        && allocf(&net.tokens, static_cast<size_t>(SQUARES) * c)
        && allocf(&net.tokTmp, static_cast<size_t>(SQUARES) * c)
        && allocf(&net.salience, SQUARES)
        && allocf(&net.h, static_cast<size_t>(SQUARES) * c)
        && allocf(&net.stalks, static_cast<size_t>(SQUARES) * STALK_DIM)
        && allocf(&net.stalkDelta, static_cast<size_t>(SQUARES) * STALK_DIM)
        && allocf(&net.update, static_cast<size_t>(SQUARES) * STALK_DIM)
        && allocf(&net.degree, SQUARES)
        && allocf(&net.node, SQUARES)
        && allocf(&net.laplacian, SQUARES)
        && allocf(&net.energy, RELATION_COUNT)
        && allocf(&net.gates, RELATION_COUNT)
        && allocf(&net.density, RELATION_COUNT)
        && allocf(&net.srcPressure, static_cast<size_t>(RELATION_COUNT) * SQUARES)
        && allocf(&net.dstPressure, static_cast<size_t>(RELATION_COUNT) * SQUARES)
        && allocf(&net.scalars, 4)
        && allocf(&net.trunk, static_cast<size_t>(c) * SQUARES)
        && allocf(&net.hidden, HIDDEN_DIM)
        && allocf(&net.policy, static_cast<size_t>(net.policySize))
        && allocf(&net.wdlLogits, WDL_OUTPUTS);
    if (!ok) return false;
    try {
        net.dBoard = sycl::malloc_device<int>(SQUARES, *net.queue);
        return net.dBoard != nullptr;
    } catch (const sycl::exception&) {
        return false;
    }
}

static Net* load_net(const std::string& path) {
    auto* net = new Net();
    try {
        auto devices = intel_gpus();
        if (devices.empty()) throw std::runtime_error("no intel gpu");
        net->queue = std::make_unique<sycl::queue>(devices.front());
        Reader in(path);
        if (in.i32() != OTIS_MAGIC) throw std::runtime_error("bad magic");
        if (in.i32() != OTIS_VERSION) throw std::runtime_error("bad version");
        net->name = in.str();
        net->inputPlanes = in.i32();
        net->channels = in.i32();
        net->blocks = in.i32();
        net->policySize = in.i32();
        if (net->inputPlanes != INPUT_PLANES || net->channels <= 0 || net->channels > MAX_CHANNELS
                || net->blocks <= 0 || net->policySize <= 0) {
            throw std::runtime_error("unsupported OTIS shape");
        }
        int c = net->channels;
        int blocks = net->blocks;
        int rd = readout_dim(c);
        long long params = 0;
        bool ok =
            upload(*net, in.floats(RAW_DIM * INPUT_PLANES), &net->rawProjW, params)
            && upload(*net, in.floats(RAW_DIM), &net->rawProjB, params)
            && upload(*net, in.floats(PIECE_DIM * PIECE_STATE_PLANES), &net->pieceProjW, params)
            && upload(*net, in.floats(PIECE_DIM), &net->pieceProjB, params)
            && upload(*net, in.floats(COORD_DIM * COORD_IN), &net->coordProjW, params)
            && upload(*net, in.floats(COORD_DIM), &net->coordProjB, params)
            && upload(*net, in.floats(c * FUSE_DIM), &net->fuseInW, params)
            && upload(*net, in.floats(c), &net->fuseInB, params)
            && upload(*net, in.floats(c), &net->fuseNormW, params)
            && upload(*net, in.floats(c), &net->fuseNormB, params)
            && upload(*net, in.floats(c * c), &net->fuseOutW, params)
            && upload(*net, in.floats(c), &net->fuseOutB, params)
            && upload(*net, in.floats(c), &net->encNormW, params)
            && upload(*net, in.floats(c), &net->encNormB, params)
            && upload(*net, in.floats(blocks * RELATION_COUNT * STALK_DIM * STALK_DIM), &net->rhoSrc, params)
            && upload(*net, in.floats(blocks * RELATION_COUNT * STALK_DIM * STALK_DIM), &net->rhoDst, params)
            && upload(*net, in.floats(blocks * RELATION_COUNT), &net->relGate, params)
            && upload(*net, in.floats(blocks), &net->etaLogits, params)
            && upload(*net, in.floats(blocks * STALK_DIM * c), &net->n2sW, params)
            && upload(*net, in.floats(blocks * STALK_DIM), &net->n2sB, params)
            && upload(*net, in.floats(blocks * c * STALK_DIM), &net->s2nW, params)
            && upload(*net, in.floats(blocks * c), &net->s2nB, params)
            && upload(*net, in.floats(blocks * c), &net->mlpNormW, params)
            && upload(*net, in.floats(blocks * c), &net->mlpNormB, params)
            && upload(*net, in.floats(blocks * c * (c * 2)), &net->mlpUpW, params)
            && upload(*net, in.floats(blocks * (c * 2)), &net->mlpUpB, params)
            && upload(*net, in.floats(blocks * (c * 2) * c), &net->mlpDownW, params)
            && upload(*net, in.floats(blocks * c), &net->mlpDownB, params)
            && upload(*net, in.floats(blocks * c), &net->blockNormW, params)
            && upload(*net, in.floats(blocks * c), &net->blockNormB, params)
            && upload(*net, in.floats(c * c), &net->triadAW, params)
            && upload(*net, in.floats(c * c), &net->triadTW, params)
            && upload(*net, in.floats(c * c), &net->triadDW, params)
            && upload(*net, in.floats(TRIAD_DIM), &net->triadNormW, params)
            && upload(*net, in.floats(TRIAD_DIM), &net->triadNormB, params)
            && upload(*net, in.floats(rd), &net->readNormW, params)
            && upload(*net, in.floats(rd), &net->readNormB, params)
            && upload(*net, in.floats(HIDDEN_DIM * rd), &net->readHidW, params)
            && upload(*net, in.floats(HIDDEN_DIM), &net->readHidB, params)
            && upload(*net, in.floats(net->policySize * HIDDEN_DIM), &net->policyW, params)
            && upload(*net, in.floats(net->policySize), &net->policyB, params)
            && upload(*net, in.floats(WDL_OUTPUTS * HIDDEN_DIM), &net->wdlW, params)
            && upload(*net, in.floats(WDL_OUTPUTS), &net->wdlB, params)
            && upload(*net, in.floats(SQUARES), &net->squareAtlas, params)
            && upload(*net, in.floats(SQUARES), &net->policyAtlas, params)
            && upload(*net, in.floats(SQUARES), &net->valueAtlas, params);
        if (!ok) throw std::runtime_error("upload failed");
        if (!in.done()) throw std::runtime_error("trailing bytes");
        net->parameterCount = params;
        if (!alloc_scratch(*net)) throw std::runtime_error("scratch alloc failed");
        return net;
    } catch (...) {
        free_net(net);
        return nullptr;
    }
}

// ---- host board reconstruction + typed relation masks (mirror Model.java) ----

static int piece_from_planes(const std::vector<float>& input, int sq) {
    for (int plane = 0; plane < 12; ++plane) {
        if (input[static_cast<size_t>(plane) * SQUARES + sq] > 0.5f) {
            int type = plane % 6 + 1;
            return plane < 6 ? type : -type;
        }
    }
    return P_EMPTY;
}

static bool is_white(int piece) { return piece > 0; }

static bool is_own(int piece, bool whiteToMove) {
    return piece != P_EMPTY && is_white(piece) == whiteToMove;
}

static int square_of(int rank, int file) {
    return rank >= 0 && rank < 8 && file >= 0 && file < 8 ? (rank << 3) + file : -1;
}

static int mask_index(int relation, int from, int to) {
    return (relation * SQUARES + from) * SQUARES + to;
}

static void set_relation(std::vector<float>& masks, int relation, int from, int to) {
    if (from != to && from >= 0 && from < SQUARES && to >= 0 && to < SQUARES) {
        masks[static_cast<size_t>(mask_index(relation, from, to))] = 1.0f;
    }
}

static int find_king(const int* board, bool white) {
    int target = white ? P_KING : -P_KING;
    for (int sq = 0; sq < SQUARES; ++sq) {
        if (board[sq] == target) return sq;
    }
    return -1;
}

static void king_zone(int kingSquare, bool* out) {
    for (int sq = 0; sq < SQUARES; ++sq) out[sq] = false;
    if (kingSquare < 0) return;
    int kr = kingSquare >> 3;
    int kf = kingSquare & 7;
    for (int sq = 0; sq < SQUARES; ++sq) {
        int r = sq >> 3;
        int f = sq & 7;
        out[sq] = sq != kingSquare && std::max(std::abs(r - kr), std::abs(f - kf)) <= 2;
    }
}

static void ray_directions(bool diagonals, bool orthogonals, int dirs[8][2], int& count) {
    count = 0;
    if (diagonals && orthogonals) {
        const int d[8][2] = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int i = 0; i < 8; ++i) { dirs[i][0] = d[i][0]; dirs[i][1] = d[i][1]; }
        count = 8;
    } else if (diagonals) {
        const int d[4][2] = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int i = 0; i < 4; ++i) { dirs[i][0] = d[i][0]; dirs[i][1] = d[i][1]; }
        count = 4;
    } else {
        const int d[4][2] = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int i = 0; i < 4; ++i) { dirs[i][0] = d[i][0]; dirs[i][1] = d[i][1]; }
        count = 4;
    }
}

static bool slider_matches(int piece, int dr, int df) {
    int type = std::abs(piece);
    bool diagonal = dr != 0 && df != 0;
    return type == P_QUEEN || (diagonal && type == P_BISHOP) || (!diagonal && type == P_ROOK);
}

static void add_tactical_edge(std::vector<float>& masks, const int* board, bool own,
        const bool* nearOwnKing, const bool* nearThemKing, int from, int to) {
    int target = board[to];
    if (own) {
        if (target == P_EMPTY) {
            if (nearThemKing[to]) set_relation(masks, 4, from, to);
        } else if (is_white(target) == is_white(board[from])) {
            set_relation(masks, 2, from, to);
        } else {
            set_relation(masks, 0, from, to);
        }
    } else {
        if (target == P_EMPTY) {
            if (nearOwnKing[to]) set_relation(masks, 5, from, to);
        } else if (is_white(target) == is_white(board[from])) {
            set_relation(masks, 3, from, to);
        } else {
            set_relation(masks, 1, from, to);
        }
    }
}

static void add_step_relations(std::vector<float>& masks, const int* board, const bool* nearOwnKing,
        const bool* nearThemKing, int from, bool own, const int deltas[][2], int deltaCount,
        bool knight, bool pawn) {
    int rank = from >> 3;
    int file = from & 7;
    for (int i = 0; i < deltaCount; ++i) {
        int to = square_of(rank + deltas[i][0], file + deltas[i][1]);
        if (to < 0) continue;
        add_tactical_edge(masks, board, own, nearOwnKing, nearThemKing, from, to);
        if (knight) set_relation(masks, 9, from, to);
        if (pawn) set_relation(masks, 10, from, to);
    }
}

static void add_ray_relations(std::vector<float>& masks, const int* board, const bool* nearOwnKing,
        const bool* nearThemKing, int from, bool own, bool diagonals, bool orthogonals) {
    int dirs[8][2];
    int count = 0;
    ray_directions(diagonals, orthogonals, dirs, count);
    int rank = from >> 3;
    int file = from & 7;
    for (int i = 0; i < count; ++i) {
        int r = rank + dirs[i][0];
        int f = file + dirs[i][1];
        while (true) {
            int to = square_of(r, f);
            if (to < 0) break;
            add_tactical_edge(masks, board, own, nearOwnKing, nearThemKing, from, to);
            if (board[to] != P_EMPTY) break;
            r += dirs[i][0];
            f += dirs[i][1];
        }
    }
}

static void add_pawn_relations(std::vector<float>& masks, const int* board, const bool* nearOwnKing,
        const bool* nearThemKing, int from, int piece, bool own) {
    int direction = is_white(piece) ? 1 : -1;
    int rank = from >> 3;
    int file = from & 7;
    const int offsets[2] = {-1, 1};
    for (int o = 0; o < 2; ++o) {
        int to = square_of(rank + direction, file + offsets[o]);
        if (to >= 0) {
            add_tactical_edge(masks, board, own, nearOwnKing, nearThemKing, from, to);
            set_relation(masks, 10, from, to);
        }
    }
}

static void add_visible_ray_mask(std::vector<float>& masks, const int* board, int from, int relation,
        bool diagonals, bool orthogonals) {
    int dirs[8][2];
    int count = 0;
    ray_directions(diagonals, orthogonals, dirs, count);
    int rank = from >> 3;
    int file = from & 7;
    for (int i = 0; i < count; ++i) {
        int r = rank + dirs[i][0];
        int f = file + dirs[i][1];
        while (true) {
            int to = square_of(r, f);
            if (to < 0) break;
            set_relation(masks, relation, from, to);
            if (board[to] != P_EMPTY) break;
            r += dirs[i][0];
            f += dirs[i][1];
        }
    }
}

static void add_slider_relation_masks(std::vector<float>& masks, const int* board, int from, int piece) {
    int type = std::abs(piece);
    if (type == P_BISHOP || type == P_QUEEN) {
        add_visible_ray_mask(masks, board, from, type == P_BISHOP ? 6 : 8, true, false);
    }
    if (type == P_ROOK || type == P_QUEEN) {
        add_visible_ray_mask(masks, board, from, type == P_ROOK ? 7 : 8, false, true);
    }
}

static void add_pin_relations(std::vector<float>& masks, const int* board, bool whiteKing) {
    int king = find_king(board, whiteKing);
    if (king < 0) return;
    int rank = king >> 3;
    int file = king & 7;
    int dirs[8][2];
    int count = 0;
    ray_directions(true, true, dirs, count);
    for (int i = 0; i < count; ++i) {
        int blocker = -1;
        int r = rank + dirs[i][0];
        int f = file + dirs[i][1];
        while (true) {
            int sq = square_of(r, f);
            if (sq < 0) break;
            int piece = board[sq];
            if (piece != P_EMPTY) {
                if (blocker < 0) {
                    if (is_white(piece) == whiteKing) {
                        blocker = sq;
                    } else {
                        break;
                    }
                } else {
                    if (is_white(piece) != whiteKing && slider_matches(piece, dirs[i][0], dirs[i][1])) {
                        set_relation(masks, 11, sq, blocker);
                    }
                    break;
                }
            }
            r += dirs[i][0];
            f += dirs[i][1];
        }
    }
}

static void add_piece_relations(std::vector<float>& masks, const int* board, bool whiteToMove,
        const bool* nearOwnKing, const bool* nearThemKing, int from, int piece) {
    int type = std::abs(piece);
    bool own = is_own(piece, whiteToMove);
    switch (type) {
        case P_PAWN:
            add_pawn_relations(masks, board, nearOwnKing, nearThemKing, from, piece, own);
            break;
        case P_KNIGHT: {
            const int deltas[8][2] = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
            add_step_relations(masks, board, nearOwnKing, nearThemKing, from, own, deltas, 8, true, false);
            break;
        }
        case P_BISHOP:
            add_ray_relations(masks, board, nearOwnKing, nearThemKing, from, own, true, false);
            break;
        case P_ROOK:
            add_ray_relations(masks, board, nearOwnKing, nearThemKing, from, own, false, true);
            break;
        case P_QUEEN:
            add_ray_relations(masks, board, nearOwnKing, nearThemKing, from, own, true, true);
            break;
        case P_KING: {
            const int deltas[8][2] = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
            add_step_relations(masks, board, nearOwnKing, nearThemKing, from, own, deltas, 8, false, false);
            break;
        }
        default:
            break;
    }
}

static std::vector<float> build_relation_masks(const int* board, bool whiteToMove) {
    std::vector<float> masks(static_cast<size_t>(RELATION_COUNT) * SQUARES * SQUARES, 0.0f);
    bool nearOwnKing[SQUARES];
    bool nearThemKing[SQUARES];
    king_zone(find_king(board, whiteToMove), nearOwnKing);
    king_zone(find_king(board, !whiteToMove), nearThemKing);
    for (int from = 0; from < SQUARES; ++from) {
        int piece = board[from];
        if (piece == P_EMPTY) continue;
        add_piece_relations(masks, board, whiteToMove, nearOwnKing, nearThemKing, from, piece);
        add_slider_relation_masks(masks, board, from, piece);
    }
    add_pin_relations(masks, board, true);
    add_pin_relations(masks, board, false);
    return masks;
}

// ---- SYCL device helpers (free functions usable inside kernels) ----

static inline float dev_gelu(float x) {
    return 0.5f * x * (1.0f + sycl::tanh(0.7978845608028654f * (x + 0.044715f * x * x * x)));
}

static inline float dev_sigmoid(float x) {
    return 1.0f / (1.0f + sycl::exp(-x));
}

static inline void dev_layernorm(float* values, const float* scale, int scaleOff,
        const float* bias, int biasOff, int length) {
    float mean = 0.0f;
    for (int i = 0; i < length; ++i) mean += values[i];
    mean /= static_cast<float>(length);
    float variance = 0.0f;
    for (int i = 0; i < length; ++i) {
        float centered = values[i] - mean;
        variance += centered * centered;
    }
    float invStd = sycl::rsqrt(variance / static_cast<float>(length) + 1.0e-5f);
    for (int i = 0; i < length; ++i) {
        values[i] = (values[i] - mean) * invStd * scale[scaleOff + i] + bias[biasOff + i];
    }
}

static inline float dev_matrix_response(const float* matrix, const float* values, int channels) {
    float sum = 0.0f;
    for (int row = 0; row < channels; ++row) {
        float rowSum = 0.0f;
        const float* r = matrix + row * channels;
        for (int col = 0; col < channels; ++col) rowSum += r[col] * values[col];
        sum += sycl::tanh(rowSum);
    }
    return sum / static_cast<float>(channels);
}

static bool predict(Net& net, const std::vector<float>& encoded,
        std::vector<float>& policy, std::vector<float>& wdl) {
    int board[SQUARES];
    bool whiteToMoveHost = encoded[12 * SQUARES] > 0.5f;
    for (int sq = 0; sq < SQUARES; ++sq) board[sq] = piece_from_planes(encoded, sq);
    std::vector<float> masks = build_relation_masks(board, whiteToMoveHost);

    sycl::queue& q = *net.queue;
    const int c = net.channels;
    const int blocks = net.blocks;
    const int policySize = net.policySize;
    const int rd = readout_dim(c);
    const int whiteToMove = whiteToMoveHost ? 1 : 0;

    // Device pointers (captured by value in kernels).
    float* input = net.dInput;
    int* dBoard = net.dBoard;
    float* dMasks = net.dMasks;
    float* tokens = net.tokens;
    float* tokTmp = net.tokTmp;
    float* salience = net.salience;
    float* h = net.h;
    float* stalks = net.stalks;
    float* stalkDelta = net.stalkDelta;
    float* update = net.update;
    float* degree = net.degree;
    float* node = net.node;
    float* laplacian = net.laplacian;
    float* energy = net.energy;
    float* gates = net.gates;
    float* density = net.density;
    float* srcPressure = net.srcPressure;
    float* dstPressure = net.dstPressure;
    float* scalars = net.scalars;
    float* trunk = net.trunk;
    float* hidden = net.hidden;
    float* policyBuf = net.policy;
    float* wdlBuf = net.wdlLogits;
    const float* rawProjW = net.rawProjW;
    const float* rawProjB = net.rawProjB;
    const float* pieceProjW = net.pieceProjW;
    const float* pieceProjB = net.pieceProjB;
    const float* coordProjW = net.coordProjW;
    const float* coordProjB = net.coordProjB;
    const float* fuseInW = net.fuseInW;
    const float* fuseInB = net.fuseInB;
    const float* fuseNormW = net.fuseNormW;
    const float* fuseNormB = net.fuseNormB;
    const float* fuseOutW = net.fuseOutW;
    const float* fuseOutB = net.fuseOutB;
    const float* encNormW = net.encNormW;
    const float* encNormB = net.encNormB;
    const float* rhoSrc = net.rhoSrc;
    const float* rhoDst = net.rhoDst;
    const float* relGate = net.relGate;
    const float* etaLogits = net.etaLogits;
    const float* n2sW = net.n2sW;
    const float* n2sB = net.n2sB;
    const float* s2nW = net.s2nW;
    const float* s2nB = net.s2nB;
    const float* mlpNormW = net.mlpNormW;
    const float* mlpNormB = net.mlpNormB;
    const float* mlpUpW = net.mlpUpW;
    const float* mlpUpB = net.mlpUpB;
    const float* mlpDownW = net.mlpDownW;
    const float* mlpDownB = net.mlpDownB;
    const float* blockNormW = net.blockNormW;
    const float* blockNormB = net.blockNormB;
    const float* triadAW = net.triadAW;
    const float* triadTW = net.triadTW;
    const float* triadDW = net.triadDW;
    const float* triadNormW = net.triadNormW;
    const float* triadNormB = net.triadNormB;
    const float* readNormW = net.readNormW;
    const float* readNormB = net.readNormB;
    const float* readHidW = net.readHidW;
    const float* readHidB = net.readHidB;
    const float* policyW = net.policyW;
    const float* policyB = net.policyB;
    const float* wdlW = net.wdlW;
    const float* wdlB = net.wdlB;
    const float* squareAtlas = net.squareAtlas;
    const float* valueAtlas = net.valueAtlas;

    try {
        q.memcpy(input, encoded.data(), encoded.size() * sizeof(float)).wait_and_throw();
        q.memcpy(dBoard, board, sizeof(board)).wait_and_throw();
        q.memcpy(dMasks, masks.data(), masks.size() * sizeof(float)).wait_and_throw();
        q.memset(energy, 0, RELATION_COUNT * sizeof(float)).wait_and_throw();
        q.memset(gates, 0, RELATION_COUNT * sizeof(float)).wait_and_throw();
        q.memset(density, 0, RELATION_COUNT * sizeof(float)).wait_and_throw();
        q.memset(srcPressure, 0, RELATION_COUNT * SQUARES * sizeof(float)).wait_and_throw();
        q.memset(dstPressure, 0, RELATION_COUNT * SQUARES * sizeof(float)).wait_and_throw();

        // Square tokens.
        q.parallel_for(sycl::range<1>(SQUARES), [=](sycl::id<1> id) {
            int sq = static_cast<int>(id[0]);
            float raw[INPUT_PLANES];
            float piece[PIECE_STATE_PLANES];
            float coord[COORD_IN];
            float fused[FUSE_DIM];
            for (int plane = 0; plane < INPUT_PLANES; ++plane) raw[plane] = input[plane * SQUARES + sq];
            int code = dBoard[sq];
            bool wtm = whiteToMove != 0;
            for (int i = 0; i < PIECE_STATE_PLANES; ++i) piece[i] = 0.0f;
            if (code == P_EMPTY) {
                piece[0] = 1.0f;
            } else {
                int type = (code < 0 ? -code : code) - 1;
                if (type >= 0 && type < 6) {
                    bool own = (code > 0) == wtm;
                    piece[(own ? 1 : 7) + type] = 1.0f;
                }
            }
            int rank = sq >> 3;
            int file = sq & 7;
            coord[0] = rank / 7.0f;
            coord[1] = file / 7.0f;
            coord[2] = (rank - 3.5f) / 3.5f;
            coord[3] = (file - 3.5f) / 3.5f;
            int rankEdge = rank < 7 - rank ? rank : 7 - rank;
            int fileEdge = file < 7 - file ? file : 7 - file;
            coord[4] = (rankEdge < fileEdge ? rankEdge : fileEdge) / 3.5f;
            coord[5] = wtm ? rank / 7.0f : (7 - rank) / 7.0f;
            for (int o = 0; o < RAW_DIM; ++o) {
                float sum = rawProjB[o];
                const float* row = rawProjW + o * INPUT_PLANES;
                for (int i = 0; i < INPUT_PLANES; ++i) sum += row[i] * raw[i];
                fused[o] = sum;
            }
            for (int o = 0; o < PIECE_DIM; ++o) {
                float sum = pieceProjB[o];
                const float* row = pieceProjW + o * PIECE_STATE_PLANES;
                for (int i = 0; i < PIECE_STATE_PLANES; ++i) sum += row[i] * piece[i];
                fused[RAW_DIM + o] = sum;
            }
            for (int o = 0; o < COORD_DIM; ++o) {
                float sum = coordProjB[o];
                const float* row = coordProjW + o * COORD_IN;
                for (int i = 0; i < COORD_IN; ++i) sum += row[i] * coord[i];
                fused[RAW_DIM + PIECE_DIM + o] = sum;
            }
            float* token = tokens + static_cast<size_t>(sq) * c;
            for (int o = 0; o < c; ++o) {
                float sum = fuseInB[o];
                const float* row = fuseInW + o * FUSE_DIM;
                for (int i = 0; i < FUSE_DIM; ++i) sum += row[i] * fused[i];
                token[o] = sum;
            }
            dev_layernorm(token, fuseNormW, 0, fuseNormB, 0, c);
            for (int o = 0; o < c; ++o) token[o] = dev_gelu(token[o]);
            float* residual = tokTmp + static_cast<size_t>(sq) * c;
            for (int o = 0; o < c; ++o) {
                float sum = fuseOutB[o];
                const float* row = fuseOutW + o * c;
                for (int i = 0; i < c; ++i) sum += row[i] * token[i];
                residual[o] = sum;
            }
            for (int o = 0; o < c; ++o) token[o] += residual[o];
            dev_layernorm(token, encNormW, 0, encNormB, 0, c);
        }).wait_and_throw();

        // Salience + h copy.
        q.parallel_for(sycl::range<1>(SQUARES), [=](sycl::id<1> id) {
            int sq = static_cast<int>(id[0]);
            const float* token = tokens + static_cast<size_t>(sq) * c;
            float mean = 0.0f;
            float magnitude = 0.0f;
            for (int i = 0; i < c; ++i) {
                float v = token[i];
                mean += v;
                magnitude += sycl::fabs(v);
            }
            mean /= static_cast<float>(c);
            magnitude /= static_cast<float>(c);
            salience[sq] = sycl::tanh(mean + 0.12f * magnitude + 0.20f * squareAtlas[sq]);
            float* hp = h + static_cast<size_t>(sq) * c;
            for (int i = 0; i < c; ++i) hp[i] = token[i];
        }).wait_and_throw();

        for (int block = 0; block < blocks; ++block) {
            // node -> stalk.
            q.parallel_for(sycl::range<1>(SQUARES), [=](sycl::id<1> id) {
                int sq = static_cast<int>(id[0]);
                int weightBase = block * STALK_DIM * c;
                int biasBase = block * STALK_DIM;
                const float* hp = h + static_cast<size_t>(sq) * c;
                float* stalk = stalks + static_cast<size_t>(sq) * STALK_DIM;
                for (int dim = 0; dim < STALK_DIM; ++dim) {
                    float sum = n2sB[biasBase + dim];
                    const float* row = n2sW + weightBase + dim * c;
                    for (int i = 0; i < c; ++i) sum += row[i] * hp[i];
                    stalk[dim] = sum;
                }
            }).wait_and_throw();

            // Single-task sheaf transport for this block.
            q.single_task([=]() {
                for (int i = 0; i < SQUARES * STALK_DIM; ++i) update[i] = 0.0f;
                for (int sq = 0; sq < SQUARES; ++sq) degree[sq] = 0.0f;
                float eta = SHEAF_ETA * 2.0f * dev_sigmoid(etaLogits[block]);
                float src[STALK_DIM];
                float dst[STALK_DIM];
                float residual[STALK_DIM];
                float srcBack[STALK_DIM];
                float dstBack[STALK_DIM];
                const float signs[RELATION_COUNT] = {
                    -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f,
                    -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f
                };
                for (int relation = 0; relation < RELATION_COUNT; ++relation) {
                    float gate = 2.0f * dev_sigmoid(relGate[block * RELATION_COUNT + relation]);
                    gates[relation] += gate;
                    float sign = signs[relation];
                    int rhoBase = ((block * RELATION_COUNT) + relation) * STALK_DIM * STALK_DIM;
                    int edgeCount = 0;
                    float energySum = 0.0f;
                    for (int from = 0; from < SQUARES; ++from) {
                        for (int to = 0; to < SQUARES; ++to) {
                            float edge = dMasks[mask_index(relation, from, to)];
                            if (edge <= 0.0f) continue;
                            edgeCount++;
                            if (block == 0) {
                                srcPressure[relation * SQUARES + from] += edge;
                                dstPressure[relation * SQUARES + to] += edge;
                            }
                            const float* sStalk = stalks + static_cast<size_t>(from) * STALK_DIM;
                            const float* dStalk = stalks + static_cast<size_t>(to) * STALK_DIM;
                            for (int j = 0; j < STALK_DIM; ++j) {
                                float sSum = 0.0f;
                                float dSum = 0.0f;
                                for (int i = 0; i < STALK_DIM; ++i) {
                                    sSum += sStalk[i] * rhoSrc[rhoBase + i * STALK_DIM + j];
                                    dSum += dStalk[i] * rhoDst[rhoBase + i * STALK_DIM + j];
                                }
                                src[j] = sSum;
                                dst[j] = dSum;
                            }
                            float norm = 0.0f;
                            for (int dim = 0; dim < STALK_DIM; ++dim) {
                                float value = dst[dim] - sign * src[dim];
                                residual[dim] = value;
                                norm += value * value;
                            }
                            energySum += edge * norm;
                            for (int j = 0; j < STALK_DIM; ++j) {
                                float sb = 0.0f;
                                float db = 0.0f;
                                for (int i = 0; i < STALK_DIM; ++i) {
                                    sb += residual[i] * rhoSrc[rhoBase + j * STALK_DIM + i];
                                    db += residual[i] * rhoDst[rhoBase + j * STALK_DIM + i];
                                }
                                srcBack[j] = sb;
                                dstBack[j] = db;
                            }
                            float scaled = gate * edge;
                            for (int dim = 0; dim < STALK_DIM; ++dim) {
                                update[from * STALK_DIM + dim] += scaled * sign * srcBack[dim];
                                update[to * STALK_DIM + dim] -= scaled * dstBack[dim];
                            }
                            degree[from] += scaled;
                            degree[to] += scaled;
                        }
                    }
                    if (block == 0) {
                        density[relation] = edgeCount / static_cast<float>(SQUARES * SQUARES);
                    }
                    if (edgeCount > 0) {
                        energy[relation] += gate * energySum / edgeCount;
                    }
                }
                for (int sq = 0; sq < SQUARES; ++sq) {
                    float invDegree = 1.0f / sycl::fmax(1.0f, degree[sq]);
                    float signed_ = 0.0f;
                    float norm = 0.0f;
                    for (int dim = 0; dim < STALK_DIM; ++dim) {
                        float delta = update[sq * STALK_DIM + dim] * invDegree;
                        stalkDelta[sq * STALK_DIM + dim] = eta * delta;
                        signed_ += delta;
                        norm += delta * delta;
                    }
                    laplacian[sq] = -signed_ / STALK_DIM;
                    node[sq] = sycl::tanh(salience[sq]
                            + 0.45f * signed_ / STALK_DIM
                            - 0.08f * sycl::sqrt(norm)
                            + 0.05f * input[17 * SQUARES + sq]);
                }
            }).wait_and_throw();

            // apply stalk update.
            q.parallel_for(sycl::range<1>(SQUARES), [=](sycl::id<1> id) {
                int sq = static_cast<int>(id[0]);
                int weightBase = block * c * STALK_DIM;
                int biasBase = block * c;
                float* hp = h + static_cast<size_t>(sq) * c;
                const float* sd = stalkDelta + static_cast<size_t>(sq) * STALK_DIM;
                for (int ch = 0; ch < c; ++ch) {
                    float sum = s2nB[biasBase + ch] * 0.05f;
                    const float* row = s2nW + weightBase + ch * STALK_DIM;
                    for (int dim = 0; dim < STALK_DIM; ++dim) sum += row[dim] * sd[dim];
                    hp[ch] += sum;
                }
            }).wait_and_throw();

            // node MLP.
            q.parallel_for(sycl::range<1>(SQUARES), [=](sycl::id<1> id) {
                int sq = static_cast<int>(id[0]);
                int upDim = c * 2;
                int normOffset = block * c;
                int upWeightBase = block * upDim * c;
                int upBiasBase = block * upDim;
                int downWeightBase = block * c * upDim;
                int downBiasBase = block * c;
                float token[MAX_CHANNELS];
                float up[2 * MAX_CHANNELS];
                float down[MAX_CHANNELS];
                float* hp = h + static_cast<size_t>(sq) * c;
                for (int i = 0; i < c; ++i) token[i] = hp[i];
                dev_layernorm(token, mlpNormW, normOffset, mlpNormB, normOffset, c);
                for (int o = 0; o < upDim; ++o) {
                    float sum = mlpUpB[upBiasBase + o];
                    const float* row = mlpUpW + upWeightBase + o * c;
                    for (int i = 0; i < c; ++i) sum += row[i] * token[i];
                    up[o] = dev_gelu(sum);
                }
                for (int ch = 0; ch < c; ++ch) {
                    float sum = mlpDownB[downBiasBase + ch];
                    const float* row = mlpDownW + downWeightBase + ch * upDim;
                    for (int i = 0; i < upDim; ++i) sum += row[i] * up[i];
                    down[ch] = hp[ch] + sum;
                }
                dev_layernorm(down, blockNormW, normOffset, blockNormB, normOffset, c);
                for (int ch = 0; ch < c; ++ch) hp[ch] = down[ch];
            }).wait_and_throw();
        }

        // Finalize sheaf.
        q.single_task([=]() {
            for (int relation = 0; relation < RELATION_COUNT; ++relation) {
                energy[relation] /= static_cast<float>(blocks);
                gates[relation] /= static_cast<float>(blocks);
            }
            for (int sq = 0; sq < SQUARES; ++sq) {
                float mean = 0.0f;
                const float* hp = h + static_cast<size_t>(sq) * c;
                for (int ch = 0; ch < c; ++ch) mean += hp[ch];
                node[sq] = sycl::tanh(0.5f * node[sq] + 0.5f * mean / c);
            }
            for (int sq = 0; sq < SQUARES; ++sq) {
                const float* hp = h + static_cast<size_t>(sq) * c;
                for (int ch = 0; ch < c; ++ch) trunk[ch * SQUARES + sq] = hp[ch];
            }
            float tension = 0.0f;
            for (int relation = 0; relation < RELATION_COUNT; ++relation) tension += energy[relation];
            tension /= static_cast<float>(RELATION_COUNT);
            float usPressure = density[0] * SQUARES * SQUARES;
            float themPressure = density[1] * SQUARES * SQUARES;
            float transport = sycl::fabs(usPressure - themPressure) / sycl::fmax(1.0f, usPressure + themPressure);
            float edgeTotal = 0.0f;
            for (int relation = 0; relation < RELATION_COUNT; ++relation) {
                edgeTotal += density[relation] * SQUARES * SQUARES;
            }
            float topology = edgeTotal / (RELATION_COUNT * SQUARES * SQUARES);
            scalars[0] = tension;
            scalars[1] = transport;
            scalars[2] = topology;
            scalars[3] = density[11];
        }).wait_and_throw();

        // Readout.
        q.single_task([=]() {
            float features[MAX_CHANNELS * 4 + RELATION_COUNT * 4 + TRIAD_DIM + BOARD_STATS_DIM];
            float means[MAX_CHANNELS];
            int cursor = 0;
            for (int ch = 0; ch < c; ++ch) {
                float sum = 0.0f;
                const float* col = trunk + ch * SQUARES;
                for (int sq = 0; sq < SQUARES; ++sq) sum += col[sq];
                means[ch] = sum / static_cast<float>(SQUARES);
                features[cursor++] = means[ch];
            }
            for (int ch = 0; ch < c; ++ch) {
                float maxv = -3.402823466e+38F;
                const float* col = trunk + ch * SQUARES;
                for (int sq = 0; sq < SQUARES; ++sq) maxv = sycl::fmax(maxv, col[sq]);
                features[cursor++] = maxv;
            }
            bool wtm = whiteToMove != 0;
            for (int side = 0; side < 2; ++side) {
                bool ownSide = side == 0;
                float acc[MAX_CHANNELS];
                for (int ch = 0; ch < c; ++ch) acc[ch] = 0.0f;
                int count = 0;
                for (int sq = 0; sq < SQUARES; ++sq) {
                    int piece = dBoard[sq];
                    if (piece == P_EMPTY) continue;
                    bool own = (piece > 0) == wtm;
                    if (own != ownSide) continue;
                    count++;
                    for (int ch = 0; ch < c; ++ch) acc[ch] += trunk[ch * SQUARES + sq];
                }
                if (count > 0) {
                    float inv = 1.0f / static_cast<float>(count);
                    for (int ch = 0; ch < c; ++ch) acc[ch] *= inv;
                }
                for (int ch = 0; ch < c; ++ch) features[cursor++] = acc[ch];
            }
            for (int relation = 0; relation < RELATION_COUNT; ++relation) features[cursor++] = energy[relation];
            for (int relation = 0; relation < RELATION_COUNT; ++relation) features[cursor++] = density[relation];
            for (int relation = 0; relation < RELATION_COUNT; ++relation) features[cursor++] = gates[relation];
            for (int relation = 0; relation < RELATION_COUNT; ++relation) {
                float sum = 0.0f;
                for (int sq = 0; sq < SQUARES; ++sq) {
                    sum += srcPressure[relation * SQUARES + sq] + dstPressure[relation * SQUARES + sq];
                }
                features[cursor++] = sum / (2.0f * SQUARES);
            }
            float tension = scalars[0];
            float transport = scalars[1];
            float topology = scalars[2];
            float pin = scalars[3];
            float triad[TRIAD_DIM];
            triad[0] = dev_matrix_response(triadAW, means, c);
            triad[1] = dev_matrix_response(triadTW, means, c);
            triad[2] = dev_matrix_response(triadDW, means, c);
            triad[3] = tension - pin + 0.25f * transport;
            dev_layernorm(triad, triadNormW, 0, triadNormB, 0, TRIAD_DIM);
            for (int i = 0; i < TRIAD_DIM; ++i) features[cursor++] = triad[i];
            int ownCount = 0;
            int themCount = 0;
            float material = 0.0f;
            float atlas = 0.0f;
            int occupied = 0;
            for (int sq = 0; sq < SQUARES; ++sq) {
                int piece = dBoard[sq];
                if (piece == P_EMPTY) continue;
                occupied++;
                atlas += valueAtlas[sq];
                int type = piece < 0 ? -piece : piece;
                float value = 0.0f;
                if (type == P_PAWN) value = 0.10f;
                else if (type == P_KNIGHT || type == P_BISHOP) value = 0.30f;
                else if (type == P_ROOK) value = 0.50f;
                else if (type == P_QUEEN) value = 0.90f;
                else if (type == P_KING) value = 0.20f;
                bool own = (piece > 0) == wtm;
                material += own ? value : -value;
                if (own) ownCount++; else themCount++;
            }
            features[cursor++] = occupied / static_cast<float>(SQUARES);
            features[cursor++] = ownCount / 16.0f;
            features[cursor++] = themCount / 16.0f;
            features[cursor++] = material / 4.0f;
            features[cursor++] = occupied == 0 ? 0.0f : atlas / occupied;
            features[cursor++] = tension;
            features[cursor++] = transport;
            features[cursor++] = topology + pin;
            dev_layernorm(features, readNormW, 0, readNormB, 0, rd);
            for (int o = 0; o < HIDDEN_DIM; ++o) {
                float sum = readHidB[o];
                const float* row = readHidW + o * rd;
                for (int i = 0; i < rd; ++i) sum += row[i] * features[i];
                hidden[o] = dev_gelu(sum);
            }
        }).wait_and_throw();

        // Policy head.
        q.parallel_for(sycl::range<1>(static_cast<size_t>(policySize)), [=](sycl::id<1> id) {
            int idx = static_cast<int>(id[0]);
            float sum = policyB[idx];
            const float* row = policyW + static_cast<size_t>(idx) * HIDDEN_DIM;
            for (int hh = 0; hh < HIDDEN_DIM; ++hh) sum += row[hh] * hidden[hh];
            policyBuf[idx] = sum;
        }).wait_and_throw();

        // Value head.
        q.single_task([=]() {
            float logits[WDL_OUTPUTS];
            for (int b = 0; b < WDL_OUTPUTS; ++b) {
                float sum = wdlB[b];
                const float* row = wdlW + b * HIDDEN_DIM;
                for (int hh = 0; hh < HIDDEN_DIM; ++hh) sum += row[hh] * hidden[hh];
                logits[b] = sum;
            }
            float maxValue = sycl::fmax(logits[0], sycl::fmax(logits[1], logits[2]));
            float a = sycl::exp(logits[0] - maxValue);
            float bb = sycl::exp(logits[1] - maxValue);
            float cc = sycl::exp(logits[2] - maxValue);
            float sum = a + bb + cc;
            float inv = 1.0f / sycl::fmax(sum, 1.0e-9f);
            wdlBuf[0] = a * inv;
            wdlBuf[1] = bb * inv;
            wdlBuf[2] = cc * inv;
        }).wait_and_throw();

        policy.assign(static_cast<size_t>(policySize), 0.0f);
        wdl.assign(WDL_OUTPUTS, 0.0f);
        q.memcpy(policy.data(), policyBuf, policy.size() * sizeof(float)).wait_and_throw();
        q.memcpy(wdl.data(), wdlBuf, wdl.size() * sizeof(float)).wait_and_throw();
    } catch (const sycl::exception&) {
        return false;
    }
    return true;
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

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_otis_oneapi_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_nn_otis_oneapi_Backend_nativeCreate(
        JNIEnv* env, jclass, jstring path) {
    std::string nativePath = jstring_to_string(env, path);
    if (nativePath.empty()) return 0L;
    return reinterpret_cast<jlong>(load_net(nativePath));
}

extern "C" JNIEXPORT void JNICALL Java_chess_nn_otis_oneapi_Backend_nativeDestroy(
        JNIEnv*, jclass, jlong handle) {
    free_net(reinterpret_cast<Net*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL Java_chess_nn_otis_oneapi_Backend_nativeGetName(
        JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net) return nullptr;
    return env->NewStringUTF(net->name.c_str());
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_nn_otis_oneapi_Backend_nativeGetInfo(
        JNIEnv* env, jclass, jlong handle) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net) return nullptr;
    jlong values[5] = {
        static_cast<jlong>(net->inputPlanes),
        static_cast<jlong>(net->channels),
        static_cast<jlong>(net->blocks),
        static_cast<jlong>(net->policySize),
        static_cast<jlong>(net->parameterCount)
    };
    jlongArray out = env->NewLongArray(5);
    if (!out) return nullptr;
    env->SetLongArrayRegion(out, 0, 5, values);
    return out;
}

extern "C" JNIEXPORT jfloat JNICALL Java_chess_nn_otis_oneapi_Backend_nativePredict(
        JNIEnv* env, jclass, jlong handle, jfloatArray encodedPlanes, jfloatArray outPolicy, jfloatArray outWdl) {
    auto* net = reinterpret_cast<Net*>(handle);
    if (!net || !encodedPlanes || !outPolicy || !outWdl) return 0.0f;
    jsize encodedLength = env->GetArrayLength(encodedPlanes);
    if (encodedLength != net->inputPlanes * SQUARES
            || env->GetArrayLength(outPolicy) != net->policySize
            || env->GetArrayLength(outWdl) != WDL_OUTPUTS) {
        return 0.0f;
    }
    std::vector<float> encoded(static_cast<size_t>(encodedLength));
    env->GetFloatArrayRegion(encodedPlanes, 0, encodedLength, encoded.data());
    std::vector<float> policy;
    std::vector<float> wdl;
    if (!predict(*net, encoded, policy, wdl)) return 0.0f;
    env->SetFloatArrayRegion(outPolicy, 0, net->policySize, policy.data());
    env->SetFloatArrayRegion(outWdl, 0, WDL_OUTPUTS, wdl.data());
    return wdl[0] - wdl[2];
}
