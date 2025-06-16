// Copyright 2024 Google LLC
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

package kelvin

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.util._
import org.scalatest.freespec.AnyFreeSpec

class CoreAxiCSRSpec extends AnyFreeSpec with ChiselSim {
  var p = new Parameters
  p.enableVector = false

  "Initialization" in {
    simulate(new CoreAxiCSR(p)) { dut =>
      dut.io.axi.read.addr.ready.expect(1)
      dut.io.axi.read.data.valid.expect(0)
      dut.io.axi.write.addr.ready.expect(1)
      dut.io.axi.write.data.ready.expect(1)
      dut.io.axi.write.resp.valid.expect(0)
    }
  }

  "Read" in {
    simulate(new CoreAxiCSR(p)) { dut =>
      dut.io.internal.poke(false.B)
      dut.io.halted.poke(false.B)
      dut.io.fault.poke(false.B)
      dut.io.kelvin_csr.value(0).poke("xCAFEB0BA".U)

      // Send read request
      dut.io.axi.read.addr.valid.poke(true.B)
      dut.io.axi.read.addr.bits.addr.poke(0x100.U)
      dut.io.axi.read.addr.ready.expect(1)
      dut.clock.step()
      dut.io.axi.read.addr.valid.poke(false.B)

      // Wait for response
      while (dut.io.axi.read.data.valid.peek().litValue != 1) {
        dut.clock.step()
      }
      dut.io.axi.read.data.valid.expect(1)
      dut.io.axi.read.data.bits.data.expect(3405689018L)
      dut.io.axi.read.data.bits.last.expect(1)
      dut.io.axi.read.data.bits.resp.expect(0)

      // Accept response, check that no requests were made after.
      dut.io.axi.read.data.ready.poke(true.B)
      for (i <- 0 until 10) {
        dut.io.axi.read.data.valid.peek()
      }
    }
  }

  "Write" in {
    simulate(new CoreAxiCSR(p)) { dut =>
      dut.io.internal.poke(false.B)
      dut.io.halted.poke(false.B)
      dut.io.fault.poke(false.B)

      // Check initial values.
      dut.io.cg.expect(1)
      dut.io.reset.expect(1)
      dut.io.pcStart.expect(0)

      // Configure write address and write data
      dut.io.axi.write.addr.valid.poke(true.B)
      dut.io.axi.write.addr.bits.addr.poke(0x4)
      dut.io.axi.write.addr.bits.len.poke(0.U)
      dut.io.axi.write.addr.bits.size.poke(2.U)
      dut.io.axi.write.addr.ready.expect(1)
      dut.io.axi.write.data.bits.data.poke((BigInt(0x20000000) << 32).U)
      dut.io.axi.write.data.bits.strb.poke(0xFF00.U)
      dut.io.axi.write.data.bits.last.poke(true.B)
      dut.io.axi.write.data.valid.poke(true.B)
      dut.io.axi.write.data.ready.expect(1)
      dut.clock.step()
      dut.io.axi.write.addr.valid.poke(false.B)
      dut.io.axi.write.data.valid.poke(false.B)

      // Wait for response
      while (dut.io.axi.write.resp.valid.peek().litValue != 1) {
        dut.clock.step()
      }
      // Check that only pcStartReg changed.
      dut.io.cg.expect(1)
      dut.io.reset.expect(1)
      dut.io.pcStart.expect(0x20000000)

      // Accept write response
      dut.io.axi.write.resp.ready.poke(true.B)
      dut.clock.step()
      dut.io.axi.write.resp.ready.poke(false.B)
      for (i <- 0 until 10) {
        dut.clock.step()
        // Check that only pcStartReg changed.
        dut.io.cg.expect(1)
        dut.io.reset.expect(1)
        dut.io.pcStart.expect(0x20000000)
        dut.io.axi.write.resp.valid.expect(0)
      }

      // Check write result via AXI, as well
      dut.io.axi.read.addr.valid.poke(true.B)
      dut.io.axi.read.addr.bits.addr.poke(0x4)
      dut.io.axi.read.addr.bits.size.poke(2.U)
      dut.io.axi.read.addr.bits.len.poke(0.U)
      while (dut.io.axi.read.addr.ready.peek().litValue != 1) {
        dut.clock.step()
      }
      dut.clock.step()
      dut.io.axi.read.addr.valid.poke(false.B)

      // Wait for read data
      while (dut.io.axi.read.data.valid.peek().litValue != 1) {
        dut.clock.step()
      }
      dut.io.axi.read.data.bits.data.expect(BigInt(0x20000000) << 32)
      dut.io.axi.read.data.bits.last.expect(1)
      dut.io.axi.read.data.bits.resp.expect(0)

      // Accept read result, no pending read
      dut.io.axi.read.data.ready.poke(true.B)
      dut.clock.step()
      dut.io.axi.read.data.valid.expect(0)
      dut.io.axi.read.data.ready.poke(false.B)
    }
  }

  "WriteInvalid" in {
    simulate(new CoreAxiCSR(p)) { dut =>
      dut.io.internal.poke(false.B)
      dut.io.halted.poke(false.B)
      dut.io.fault.poke(false.B)

      // Check initial values.
      dut.io.cg.expect(1)
      dut.io.reset.expect(1)
      dut.io.pcStart.expect(0)

      // Configure write address and write data
      dut.io.axi.write.addr.valid.poke(true.B)
      dut.io.axi.write.addr.bits.addr.poke(0x104)
      dut.io.axi.write.addr.bits.len.poke(0.U)
      dut.io.axi.write.addr.bits.size.poke(2.U)
      dut.io.axi.write.addr.ready.expect(1)
      dut.io.axi.write.data.bits.data.poke((BigInt(0x20000000) << 32).U)
      dut.io.axi.write.data.bits.strb.poke(0xFF00.U)
      dut.io.axi.write.data.bits.last.poke(true.B)
      dut.io.axi.write.data.valid.poke(true.B)
      dut.io.axi.write.data.ready.expect(1)
      dut.clock.step()
      dut.io.axi.write.addr.valid.poke(false.B)
      dut.io.axi.write.data.valid.poke(false.B)

      // Wait for response
      while (dut.io.axi.write.resp.valid.peek().litValue != 1) {
        dut.clock.step()
      }
      // Check error was raised in response
      dut.io.axi.write.resp.bits.resp.expect(2)
      // Check that no register changed.
      dut.io.cg.expect(1)
      dut.io.reset.expect(1)
      dut.io.pcStart.expect(0)

      // Accept write response
      dut.io.axi.write.resp.ready.poke(true.B)
      dut.clock.step()
      dut.io.axi.write.resp.ready.poke(false.B)

      // Check that no register changed.
      dut.io.cg.expect(1)
      dut.io.reset.expect(1)
      dut.io.pcStart.expect(0)
    }
  }
}
