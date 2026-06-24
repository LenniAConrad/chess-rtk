#!/usr/bin/env python3
"""Convert an LC0 BT4 .pb.gz network to a CRTK BT4 .bin file (version 2).

The output format is consumed by ``chess.nn.lc0.bt4.BinLoader``. Weights are
written as raw little-endian float32 row-major tensors with shape ``[out, in]``
for dense layers, matching the Java reference forward pass.

Usage::

    ./scripts/convert_lc0_bt4_to_bin.py --in models/BT4-...pb.gz --out models/bt4.bin

Conversion requires the ``numpy`` and ``protobuf`` Python packages;
``net_pb2.py`` is generated lazily from ``scripts/proto/net.proto``.
"""

import argparse
import gzip
import struct
import subprocess
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
PROTO_DIR = SCRIPT_DIR / "proto"
np = None
net_pb2 = None


def ensure_proto_module():
    """Generate net_pb2 next to the proto file if missing."""
    pb2_path = PROTO_DIR / "net_pb2.py"
    proto_path = PROTO_DIR / "net.proto"
    if not pb2_path.exists() or proto_path.stat().st_mtime > pb2_path.stat().st_mtime:
        subprocess.run(["protoc", f"--python_out={PROTO_DIR}", "net.proto"],
                       cwd=PROTO_DIR, check=True)
    sys.path.insert(0, str(PROTO_DIR))


def load_converter_dependencies():
    """Load optional conversion dependencies after argparse has handled --help."""
    global np
    global net_pb2
    if np is None:
        import numpy as numpy_module
        np = numpy_module
    if net_pb2 is None:
        ensure_proto_module()
        import net_pb2 as net_pb2_module
        net_pb2 = net_pb2_module


MAGIC = 0x4A345442  # "BT4J"
VERSION = 2

# CRTK Activation names (must match chess.nn.lc0.bt4.Network.Activation).
ACT_NONE = "NONE"
ACT_RELU = "RELU"
ACT_MISH = "MISH"
ACT_SWISH = "SWISH"
ACT_TANH = "TANH"

# LC0 ActivationFunction enum values.
LC0_ACT_DEFAULT = 0
LC0_ACT_MISH = 1
LC0_ACT_RELU = 2
LC0_ACT_NONE = 3
LC0_ACT_TANH = 4
LC0_ACT_SWISH = 7

# LC0 DefaultActivation enum values.
LC0_DEFAULT_RELU = 0
LC0_DEFAULT_MISH = 1

# LC0 Layer.Encoding.
ENC_UNKNOWN = 0
ENC_LINEAR16 = 1
ENC_FLOAT16 = 2
ENC_BFLOAT16 = 3
ENC_FLOAT32 = 4

# Hardcoded LC0 BT4 input preproc slice width.
PREPROC_CHANNELS_PER_TOKEN = 12


def decode_layer(layer, default_encoding=ENC_LINEAR16):
    """Decode an LC0 Layer message into a float32 numpy array."""
    if layer is None or not layer.params:
        return None
    encoding = layer.encoding or default_encoding
    raw = layer.params
    if encoding == ENC_FLOAT32:
        arr = np.frombuffer(raw, dtype="<f4").copy()
    elif encoding == ENC_FLOAT16:
        arr = np.frombuffer(raw, dtype="<f2").astype(np.float32)
    elif encoding == ENC_BFLOAT16:
        bf16 = np.frombuffer(raw, dtype="<u2")
        arr = (bf16.astype(np.uint32) << 16).view(np.float32).copy()
    elif encoding == ENC_LINEAR16:
        u16 = np.frombuffer(raw, dtype="<u2").astype(np.float32)
        mn = float(layer.min_val)
        mx = float(layer.max_val)
        arr = mn + (mx - mn) * (u16 / 65535.0)
    else:
        raise ValueError(f"Unsupported Layer.Encoding: {encoding}")
    return arr.astype(np.float32, copy=False)


def need_layer(layer, name):
    arr = decode_layer(layer)
    if arr is None or arr.size == 0:
        raise ValueError(f"Required layer missing or empty: {name}")
    return arr


def map_activation(value, default_value):
    """Map LC0 ActivationFunction -> CRTK Activation name."""
    effective = value if value != LC0_ACT_DEFAULT else default_value
    if effective == LC0_ACT_MISH:
        return ACT_MISH
    if effective == LC0_ACT_RELU:
        return ACT_RELU
    if effective == LC0_ACT_NONE:
        return ACT_NONE
    if effective == LC0_ACT_TANH:
        return ACT_TANH
    if effective == LC0_ACT_SWISH:
        return ACT_SWISH
    raise ValueError(f"Unsupported activation: {effective}")


def map_default_activation(value):
    """Map LC0 DefaultActivation -> CRTK Activation name."""
    if value == LC0_DEFAULT_MISH:
        return ACT_MISH
    if value == LC0_DEFAULT_RELU:
        return ACT_RELU
    raise ValueError(f"Unsupported default activation: {value}")


def write_i32(out, value):
    out.write(struct.pack("<i", int(value)))


def write_f32(out, value):
    out.write(struct.pack("<f", float(value)))


def write_bool(out, value):
    out.write(b"\x01" if value else b"\x00")


def write_string(out, value):
    data = value.encode("utf-8")
    write_i32(out, len(data))
    out.write(data)


def write_float_array(out, arr):
    arr = np.ascontiguousarray(np.asarray(arr, dtype=np.float32))
    write_i32(out, int(arr.size))
    out.write(arr.tobytes())


def write_dense(out, w, b, in_dim, out_dim):
    """Write a dense layer with row-major [out, in] weights."""
    if w.size != in_dim * out_dim:
        raise ValueError(f"dense size mismatch: {w.size} vs {in_dim}*{out_dim}")
    if b.size != out_dim:
        raise ValueError(f"dense bias size mismatch: {b.size} vs {out_dim}")
    write_i32(out, in_dim)
    write_i32(out, out_dim)
    write_float_array(out, w)
    write_float_array(out, b)


def write_architecture(out, arch):
    write_string(out, arch["name"])
    write_string(out, arch["inputFormat"])
    write_string(out, arch["inputEmbedding"])
    write_i32(out, arch["inputChannels"])
    write_i32(out, arch["tokens"])
    write_i32(out, arch["embeddingSize"])
    write_i32(out, arch["encoderLayers"])
    write_i32(out, arch["attentionHeads"])
    write_i32(out, arch["policySize"])
    write_f32(out, arch["layerNormEpsilon"])
    write_i32(out, arch["ffnHiddenSize"])
    write_i32(out, arch["smolgenHiddenChannels"])
    write_i32(out, arch["smolgenHiddenSize"])
    write_i32(out, arch["smolgenPerHeadDim"])
    write_i32(out, arch["smolgenGlobalSize"])
    write_string(out, arch["defaultActivation"])
    write_string(out, arch["smolgenActivation"])
    write_string(out, arch["ffnActivation"])
    write_bool(out, arch["hasInputPreproc"])
    write_bool(out, arch["hasInputEmbFfn"])
    write_bool(out, arch["hasInputGates"])
    write_bool(out, arch["hasSmolgen"])


def write_smolgen(out, mha, embedding_size, heads):
    """Write one MHA's smolgen block (compress, dense1, ln1, dense2, ln2)."""
    sg = mha.smolgen
    compress = need_layer(sg.compress, "smolgen.compress")
    hidden_channels = compress.size // embedding_size
    compress_b = np.zeros(hidden_channels, dtype=np.float32)
    write_dense(out, compress, compress_b, embedding_size, hidden_channels)

    d1_w = need_layer(sg.dense1_w, "smolgen.dense1_w")
    d1_b = need_layer(sg.dense1_b, "smolgen.dense1_b")
    smolgen_hidden_size = d1_b.size
    smolgen_compress_flat = (smolgen_hidden_size and (d1_w.size // smolgen_hidden_size)) or 0
    write_dense(out, d1_w, d1_b, smolgen_compress_flat, smolgen_hidden_size)
    write_float_array(out, need_layer(sg.ln1_gammas, "smolgen.ln1_gammas"))
    write_float_array(out, need_layer(sg.ln1_betas, "smolgen.ln1_betas"))

    d2_w = need_layer(sg.dense2_w, "smolgen.dense2_w")
    d2_b = need_layer(sg.dense2_b, "smolgen.dense2_b")
    smolgen_total = d2_b.size
    write_dense(out, d2_w, d2_b, smolgen_hidden_size, smolgen_total)
    write_float_array(out, need_layer(sg.ln2_gammas, "smolgen.ln2_gammas"))
    write_float_array(out, need_layer(sg.ln2_betas, "smolgen.ln2_betas"))


def write_attention(out, mha, embedding_size, heads, has_smolgen):
    write_i32(out, heads)
    q_w = need_layer(mha.q_w, "mha.q_w")
    q_b = need_layer(mha.q_b, "mha.q_b")
    k_w = need_layer(mha.k_w, "mha.k_w")
    k_b = need_layer(mha.k_b, "mha.k_b")
    v_w = need_layer(mha.v_w, "mha.v_w")
    v_b = need_layer(mha.v_b, "mha.v_b")
    out_w = need_layer(mha.dense_w, "mha.dense_w")
    out_b = need_layer(mha.dense_b, "mha.dense_b")
    d_model = q_b.size
    write_dense(out, q_w, q_b, embedding_size, d_model)
    write_dense(out, k_w, k_b, embedding_size, d_model)
    write_dense(out, v_w, v_b, embedding_size, d_model)
    write_dense(out, out_w, out_b, d_model, embedding_size)
    if has_smolgen:
        write_smolgen(out, mha, embedding_size, heads)


def write_encoder_block(out, layer, embedding_size, heads, ffn_hidden, activation_name, alpha,
                        has_smolgen):
    write_attention(out, layer.mha, embedding_size, heads, has_smolgen)
    f1_w = need_layer(layer.ffn.dense1_w, "ffn.dense1_w")
    f1_b = need_layer(layer.ffn.dense1_b, "ffn.dense1_b")
    f2_w = need_layer(layer.ffn.dense2_w, "ffn.dense2_w")
    f2_b = need_layer(layer.ffn.dense2_b, "ffn.dense2_b")
    write_dense(out, f1_w, f1_b, embedding_size, ffn_hidden)
    write_dense(out, f2_w, f2_b, ffn_hidden, embedding_size)
    write_float_array(out, need_layer(layer.ln1_gammas, "ln1_gammas"))
    write_float_array(out, need_layer(layer.ln1_betas, "ln1_betas"))
    write_float_array(out, need_layer(layer.ln2_gammas, "ln2_gammas"))
    write_float_array(out, need_layer(layer.ln2_betas, "ln2_betas"))
    write_string(out, activation_name)
    write_f32(out, alpha)


def write_input_stack(out, weights, embedding_size, tokens, default_activation_name,
                      input_embedding):
    if input_embedding == "PE_DENSE":
        preproc_w = need_layer(weights.ip_emb_preproc_w, "ip_emb_preproc_w")
        preproc_b = need_layer(weights.ip_emb_preproc_b, "ip_emb_preproc_b")
        preproc_in = tokens * PREPROC_CHANNELS_PER_TOKEN
        preproc_out = preproc_b.size
        if preproc_out % tokens != 0:
            raise ValueError("preproc bias not divisible by tokens")
        write_dense(out, preproc_w, preproc_b, preproc_in, preproc_out)

    emb_w = need_layer(weights.ip_emb_w, "ip_emb_w")
    emb_b = need_layer(weights.ip_emb_b, "ip_emb_b")
    if emb_b.size != embedding_size:
        raise ValueError(f"ip_emb_b size {emb_b.size} != embedding {embedding_size}")
    emb_in = emb_w.size // embedding_size
    write_dense(out, emb_w, emb_b, emb_in, embedding_size)

    if input_embedding == "PE_DENSE":
        write_float_array(out, need_layer(weights.ip_emb_ln_gammas, "ip_emb_ln_gammas"))
        write_float_array(out, need_layer(weights.ip_emb_ln_betas, "ip_emb_ln_betas"))

    # Gates: LC0 stores as [embedding_size, tokens] row-major and transposes
    # to [tokens, embedding_size] for ONNX. The Java forward pass iterates
    # values as [tokens, embedding_size] row-major, so we must transpose here.
    mult_gate = need_layer(weights.ip_mult_gate, "ip_mult_gate").reshape(
        embedding_size, 64).T.reshape(-1)
    add_gate = need_layer(weights.ip_add_gate, "ip_add_gate").reshape(
        embedding_size, 64).T.reshape(-1)
    write_float_array(out, np.ascontiguousarray(mult_gate))
    write_float_array(out, np.ascontiguousarray(add_gate))

    # Embedding FFN + LN
    ffn = weights.ip_emb_ffn
    f1_w = need_layer(ffn.dense1_w, "ip_emb_ffn.dense1_w")
    f1_b = need_layer(ffn.dense1_b, "ip_emb_ffn.dense1_b")
    f2_w = need_layer(ffn.dense2_w, "ip_emb_ffn.dense2_w")
    f2_b = need_layer(ffn.dense2_b, "ip_emb_ffn.dense2_b")
    write_dense(out, f1_w, f1_b, embedding_size, f1_b.size)
    write_dense(out, f2_w, f2_b, f1_b.size, embedding_size)
    write_float_array(out, need_layer(weights.ip_emb_ffn_ln_gammas, "ip_emb_ffn_ln_gammas"))
    write_float_array(out, need_layer(weights.ip_emb_ffn_ln_betas, "ip_emb_ffn_ln_betas"))


def write_policy_head(out, weights, policy_head_field, embedding_size, default_activation):
    """Write the chosen policy head + its shared embedding layer."""
    shared_w = need_layer(weights.policy_heads.ip_pol_w, "policy_heads.ip_pol_w")
    shared_b = need_layer(weights.policy_heads.ip_pol_b, "policy_heads.ip_pol_b")
    write_dense(out, shared_w, shared_b, embedding_size, shared_b.size)
    # No policy-only encoder blocks (BT4 policy head has none in this model).
    write_i32(out, 0)
    head = getattr(weights.policy_heads, policy_head_field)
    q_w = need_layer(head.ip2_pol_w, f"policy_heads.{policy_head_field}.ip2_pol_w")
    q_b = need_layer(head.ip2_pol_b, f"policy_heads.{policy_head_field}.ip2_pol_b")
    k_w = need_layer(head.ip3_pol_w, f"policy_heads.{policy_head_field}.ip3_pol_w")
    k_b = need_layer(head.ip3_pol_b, f"policy_heads.{policy_head_field}.ip3_pol_b")
    write_dense(out, q_w, q_b, shared_b.size, q_b.size)
    write_dense(out, k_w, k_b, shared_b.size, k_b.size)
    promo = need_layer(head.ip4_pol_w, f"policy_heads.{policy_head_field}.ip4_pol_w")
    write_float_array(out, promo)
    # The shared embedding is run through the network's default activation (MISH for BT4).
    write_string(out, default_activation)


def write_value_head(out, weights, value_head_field, embedding_size):
    head = getattr(weights.value_heads, value_head_field)
    e_w = need_layer(head.ip_val_w, f"value_heads.{value_head_field}.ip_val_w")
    e_b = need_layer(head.ip_val_b, f"value_heads.{value_head_field}.ip_val_b")
    val_emb = e_b.size  # 128 for BT4
    write_dense(out, e_w, e_b, embedding_size, val_emb)
    fc1_w = need_layer(head.ip1_val_w, f"value_heads.{value_head_field}.ip1_val_w")
    fc1_b = need_layer(head.ip1_val_b, f"value_heads.{value_head_field}.ip1_val_b")
    fc1_in = fc1_w.size // fc1_b.size
    write_dense(out, fc1_w, fc1_b, fc1_in, fc1_b.size)
    fc2_w = need_layer(head.ip2_val_w, f"value_heads.{value_head_field}.ip2_val_w")
    fc2_b = need_layer(head.ip2_val_b, f"value_heads.{value_head_field}.ip2_val_b")
    write_dense(out, fc2_w, fc2_b, fc1_b.size, fc2_b.size)
    # Value head embedding activation is also the network default.
    write_string(out, ACT_MISH)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--in", dest="input", required=True,
                        help="Path to LC0 BT4 .pb.gz")
    parser.add_argument("--out", dest="output", required=True,
                        help="Path to write the CRTK BT4 .bin")
    parser.add_argument("--name", default="lc0-bt4-1024x15x32h",
                        help="Architecture name string written into the bin header")
    parser.add_argument("--input-format", default="BT4_CANONICAL_112",
                        choices=["CLASSICAL_112", "CASTLING_PLANE_112", "BT4_CANONICAL_112"])
    parser.add_argument("--policy-head", default="vanilla",
                        choices=["vanilla", "optimistic_st", "soft", "opponent"])
    parser.add_argument("--value-head", default="winner",
                        choices=["winner", "q", "st"])
    args = parser.parse_args()
    load_converter_dependencies()

    in_path = Path(args.input).expanduser().resolve()
    out_path = Path(args.output).expanduser().resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"reading {in_path}")
    with gzip.open(in_path, "rb") as fh:
        net = net_pb2.Net()
        net.ParseFromString(fh.read())

    network_format = net.format.network_format
    default_activation_value = network_format.default_activation
    smolgen_activation_value = network_format.smolgen_activation
    ffn_activation_value = network_format.ffn_activation

    default_activation_name = map_default_activation(default_activation_value)
    smolgen_activation_name = map_activation(smolgen_activation_value, default_activation_value)
    ffn_activation_name = map_activation(ffn_activation_value, default_activation_value)

    weights = net.weights
    if len(weights.encoder) == 0:
        raise ValueError("BT4 weights have no encoder blocks")
    first_block = weights.encoder[0]
    embedding_size = decode_layer(weights.ip_emb_b).size
    heads = decode_layer(first_block.mha.q_b).size // (decode_layer(first_block.mha.q_b).size // 32)
    # Recompute heads via ip_emb_b size and headcount field if present.
    if weights.HasField("headcount") if hasattr(weights, "HasField") else False:
        heads = weights.headcount
    elif weights.headcount > 0:
        heads = weights.headcount
    else:
        # Fallback: assume 32 heads for the standard BT4 family.
        heads = 32

    # Smolgen presence
    has_smolgen = first_block.mha.HasField("smolgen")
    smolgen_w_global = decode_layer(weights.smolgen_w) if has_smolgen else None
    # Smolgen sub-dims (from first block).
    if has_smolgen:
        compress = decode_layer(first_block.mha.smolgen.compress)
        smolgen_hidden_channels = compress.size // embedding_size
        d1_b = decode_layer(first_block.mha.smolgen.dense1_b)
        smolgen_hidden_size = d1_b.size
        d2_b = decode_layer(first_block.mha.smolgen.dense2_b)
        smolgen_total = d2_b.size
        if smolgen_total % heads != 0:
            raise ValueError("smolgen dense2 output not divisible by heads")
        smolgen_per_head = smolgen_total // heads
        smolgen_global = smolgen_w_global.size // smolgen_per_head
    else:
        smolgen_hidden_channels = 1
        smolgen_hidden_size = 1
        smolgen_per_head = 1
        smolgen_global = 64 * 64

    # FFN hidden size (from first block).
    ffn_hidden = decode_layer(first_block.ffn.dense1_b).size

    # Input stack presence flags.
    has_input_preproc = bool(weights.ip_emb_preproc_b.params)
    has_input_emb_ffn = bool(weights.ip_emb_ffn.dense1_b.params)
    has_input_gates = bool(weights.ip_mult_gate.params) or bool(weights.ip_add_gate.params)
    input_embedding_kind = "PE_DENSE" if has_input_preproc else "NONE"

    arch = {
        "name": args.name,
        "inputFormat": args.input_format,
        "inputEmbedding": input_embedding_kind,
        "inputChannels": 112,
        "tokens": 64,
        "embeddingSize": embedding_size,
        "encoderLayers": len(weights.encoder),
        "attentionHeads": heads,
        "policySize": 1858,
        "layerNormEpsilon": 1.0e-3,
        "ffnHiddenSize": ffn_hidden,
        "smolgenHiddenChannels": smolgen_hidden_channels,
        "smolgenHiddenSize": smolgen_hidden_size,
        "smolgenPerHeadDim": smolgen_per_head,
        "smolgenGlobalSize": smolgen_global,
        "defaultActivation": default_activation_name,
        "smolgenActivation": smolgen_activation_name,
        "ffnActivation": ffn_activation_name,
        "hasInputPreproc": has_input_preproc,
        "hasInputEmbFfn": has_input_emb_ffn,
        "hasInputGates": has_input_gates,
        "hasSmolgen": has_smolgen,
    }
    print(f"architecture: {arch}")

    encoder_alpha = (2.0 * len(weights.encoder)) ** -0.25
    print(f"writing {out_path}")
    with open(out_path, "wb") as out:
        write_i32(out, MAGIC)
        write_i32(out, VERSION)
        write_architecture(out, arch)
        write_input_stack(out, weights, embedding_size, 64,
                          default_activation_name, input_embedding_kind)
        write_i32(out, len(weights.encoder))
        for i, block in enumerate(weights.encoder):
            write_encoder_block(out, block, embedding_size, heads, ffn_hidden,
                                default_activation_name, encoder_alpha, has_smolgen)
            if (i + 1) % 5 == 0 or i == len(weights.encoder) - 1:
                print(f"  encoder block {i+1}/{len(weights.encoder)}")
        if has_smolgen:
            write_float_array(out, smolgen_w_global)
        write_policy_head(out, weights, args.policy_head, embedding_size, default_activation_name)
        write_value_head(out, weights, args.value_head, embedding_size)
    print(f"wrote {out_path.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
