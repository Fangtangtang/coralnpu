// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package bus

import chisel3._
import chisel3.util._


/**
  * Contains pure combinational functions for calculating SECDED ECC codes.
  * These are based on the `prim_secded_inv` functions from OpenTitan to ensure
  * compatibility.
  */
object Secded {
  /**
    * Calculates a 39-bit word (32-bit data, 7-bit ECC) using the same
    * logic as OpenTitan's `prim_secded_inv_39_32_enc`.
    */
  def ecc39_32(data: UInt): UInt = {
    val checksum = Wire(Vec(7, Bool()))

    // ECC bit calculation based on Verilog implementation.
    checksum(0) := (data & "h002606BD25".U).xorR
    checksum(1) := (data & "h00DEBA8050".U).xorR
    checksum(2) := (data & "h00413D89AA".U).xorR
    checksum(3) := (data & "h0031234ED1".U).xorR
    checksum(4) := (data & "h00C2C1323B".U).xorR
    checksum(5) := (data & "h002DCC624C".U).xorR
    checksum(6) := (data & "h0098505586".U).xorR

    // Final inversion for `secded_inv` compatibility.
    val data_o = Cat(checksum.asUInt, data)
    data_o.asUInt ^ "h2A00000000".U
  }

  /**
    * Calculates a 64-bit word (57-bit data, 7-bit ECC) using the same
    * logic as OpenTitan's `prim_secded_inv_64_57_enc`.
    */
  def ecc64_57(data: UInt): UInt = {
    val checksum = Wire(Vec(7, Bool()))

    // ECC bit calculation based on Verilog implementation.
    checksum(0) := (data & "h0103FFF800007FFF".U).xorR
    checksum(1) := (data & "h017C1FF801FF801F".U).xorR
    checksum(2) := (data & "h01BDE1F87E0781E1".U).xorR
    checksum(3) := (data & "h01DEEE3B8E388E22".U).xorR
    checksum(4) := (data & "h01EF76CDB2C93244".U).xorR
    checksum(5) := (data & "h01F7BB56D5525488".U).xorR
    checksum(6) := (data & "h01FBDDA769A46910".U).xorR

    // Final inversion for `secded_inv` compatibility.
    val data_o = Cat(checksum.asUInt, data)
    data_o.asUInt ^ "h5400000000000000".U
  }
}

/**
  * A parameterized SECDED encoder.
  *
  * @param DATA_W The width of the data input. Supported values are 32, 57, 128.
  */
class SecdedEncoder(val DATA_W: Int) extends Module {
  override val desiredName = s"SecdedEncoder_${DATA_W}"
  val IO_W = DATA_W match {
    case 32 => 39
    case 57 => 64
    case 128 => 128 + 7 // 128-bit data uses a 7-bit folded ECC.
  }
  val ECC_W = IO_W - DATA_W

  val io = IO(new Bundle {
    val data_i = Input(UInt(DATA_W.W))
    val data_o = Output(UInt(IO_W.W))
    val ecc_o = Output(UInt(ECC_W.W))
  })

  if (DATA_W == 32) {
    io.data_o := Secded.ecc39_32(io.data_i)
  } else if (DATA_W == 57) {
    io.data_o := Secded.ecc64_57(io.data_i)
  } else if (DATA_W == 128) {
    // For 128-bit data, we use the "folding" scheme: the data is split into
    // four 32-bit chunks, and their 7-bit ECC codes are XORed together.
    val ecc0 = Secded.ecc39_32(io.data_i(31, 0))(38, 32)
    val ecc1 = Secded.ecc39_32(io.data_i(63, 32))(38, 32)
    val ecc2 = Secded.ecc39_32(io.data_i(95, 64))(38, 32)
    val ecc3 = Secded.ecc39_32(io.data_i(127, 96))(38, 32)
    io.data_o := Cat(ecc0 ^ ecc1 ^ ecc2 ^ ecc3, io.data_i)
  } else {
    // Ensure we don't try to synthesize for an unsupported width.
    assert(false, "Unsupported DATA_W for SecdedEncoder")
    io.data_o := 0.U // Default assignment to avoid compilation errors
  }

  // Convenient output for just the ECC bits.
  io.ecc_o := io.data_o(IO_W - 1, DATA_W)
}

/**
  * Generates TileLink integrity fields for the A-channel (Request).
  */
object RequestIntegrityGen {
  def apply(tlul_p: TLULParameters, a_i: OpenTitanTileLink.A_Channel): OpenTitanTileLink.A_Channel = {
    val req_intg_gen = Module(new RequestIntegrityGen(tlul_p))
    req_intg_gen.io.a_i := a_i
    req_intg_gen.io.a_o
  }
}

class RequestIntegrityGen(p: TLULParameters) extends Module {
  override val desiredName = s"RequestIntegrityGen_${p.w}"
  val io = IO(new Bundle {
    val a_i = Input(new OpenTitanTileLink.A_Channel(p))
    val a_o = Output(new OpenTitanTileLink.A_Channel(p))
  })
  // Ensure that we don't optimize out any parts of the bundle, at least
  // via the Chisel toolchain.
  dontTouch(io.a_i)
  dontTouch(io.a_o)

  // Passthrough for most fields.
  io.a_o := io.a_i

  // Recreate the tl_h2d_cmd_intg_t struct for command integrity.
  val cmd_w = 57
  val cmd_data = Wire(UInt(cmd_w.W))
  cmd_data := Cat(
    io.a_i.user.instr_type,
    io.a_i.address,
    io.a_i.opcode,
    io.a_i.mask
  )

  val cmd_encoder = Module(new SecdedEncoder(cmd_w))
  cmd_encoder.io.data_i := cmd_data
  io.a_o.user.cmd_intg := cmd_encoder.io.ecc_o

  // Data integrity calculation.
  val data_encoder = Module(new SecdedEncoder(p.w * 8))
  data_encoder.io.data_i := io.a_i.data
  io.a_o.user.data_intg := data_encoder.io.ecc_o
}

/**
  * Checks TileLink integrity fields for the A-channel (Request).
  */
class RequestIntegrityCheck(p: TLULParameters) extends Module {
  override val desiredName = s"RequestIntegrityCheck_${p.w}"
  val io = IO(new Bundle {
    val a_i = Input(new OpenTitanTileLink.A_Channel(p))
    val fault = Output(Bool())
  })

  // Recreate the tl_h2d_cmd_intg_t struct for command integrity.
  val cmd_w = 57
  val cmd_data = Wire(UInt(cmd_w.W))
  cmd_data := Cat(
    io.a_i.user.instr_type,
    io.a_i.address,
    io.a_i.opcode,
    io.a_i.mask
  )

  val cmd_encoder = Module(new SecdedEncoder(cmd_w))
  cmd_encoder.io.data_i := cmd_data
  val expected_cmd_intg = cmd_encoder.io.ecc_o

  // Data integrity calculation.
  val data_encoder = Module(new SecdedEncoder(p.w * 8))
  data_encoder.io.data_i := io.a_i.data
  val expected_data_intg = data_encoder.io.ecc_o

  // A fault is generated if the received integrity does not match the
  // calculated integrity.
  io.fault := (expected_cmd_intg =/= io.a_i.user.cmd_intg) ||
              (expected_data_intg =/= io.a_i.user.data_intg)
}

/**
  * Generates TileLink integrity fields for the D-channel (Response).
  */
class ResponseIntegrityGen(p: TLULParameters) extends Module {
  override val desiredName = s"ResponseIntegrityGen_${p.w}"
  val io = IO(new Bundle {
    val d_i = Input(new OpenTitanTileLink.D_Channel(p))
    val d_o = Output(new OpenTitanTileLink.D_Channel(p))
  })
  // Ensure that we don't optimize out any parts of the bundle, at least
  // via the Chisel toolchain.
  dontTouch(io.d_i)
  dontTouch(io.d_o)

  // Passthrough for most fields.
  io.d_o := io.d_i

  // Recreate the tl_d2h_rsp_intg_t struct for response integrity.
  val rsp_w = 57
  val rsp_data = Wire(UInt(rsp_w.W))
  rsp_data := Cat(
    io.d_i.opcode,
    io.d_i.size,
    io.d_i.error
  )


  val rsp_encoder = Module(new SecdedEncoder(rsp_w))
  rsp_encoder.io.data_i := rsp_data
  io.d_o.user.rsp_intg := rsp_encoder.io.ecc_o

  // Data integrity calculation.
  val data_encoder = Module(new SecdedEncoder(p.w * 8))
  data_encoder.io.data_i := io.d_i.data
  io.d_o.user.data_intg := data_encoder.io.ecc_o
}

/**
  * Checks TileLink integrity fields for the D-channel (Response).
  */
class ResponseIntegrityCheck(p: TLULParameters) extends Module {
  override val desiredName = s"ResponseIntegrityCheck_${p.w}"
  val io = IO(new Bundle {
    val d_i = Input(new OpenTitanTileLink.D_Channel(p))
    val fault = Output(Bool())
  })

  // Recreate the tl_d2h_rsp_intg_t struct for response integrity.
  val rsp_w = 57
  val rsp_data = Wire(UInt(rsp_w.W))
  rsp_data := Cat(
    io.d_i.opcode,
    io.d_i.size,
    io.d_i.error
  )

  val rsp_encoder = Module(new SecdedEncoder(rsp_w))
  rsp_encoder.io.data_i := rsp_data
  val expected_rsp_intg = rsp_encoder.io.ecc_o

  // Data integrity calculation.
  val data_encoder = Module(new SecdedEncoder(p.w * 8))
  data_encoder.io.data_i := io.d_i.data
  val expected_data_intg = data_encoder.io.ecc_o

  // A fault is generated if the received integrity does not match the
  // calculated integrity.
  io.fault := (expected_rsp_intg =/= io.d_i.user.rsp_intg) ||
              (expected_data_intg =/= io.d_i.user.data_intg)
}