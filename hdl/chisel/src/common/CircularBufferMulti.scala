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

package common

import chisel3._
import chisel3.util._

class CircularBufferMulti[T <: Data](t: T, n: Int, capacity: Int) extends Module {
  // For the time being, restrict to powers of 2
  assert(isPow2(n))
  assert(isPow2(capacity))
  val io = IO(new Bundle {
    val enqValid = Input(UInt(log2Ceil(n + 1).W))
    val enqData = Input(Vec(n, t))

    val nEnqueued = Output(UInt(log2Ceil(capacity + 1).W))
    val nSpace = Output(UInt(log2Ceil(capacity + 1).W))

    val dataOut = Output(Vec(n, t))
    val deqReady = Input(UInt(log2Ceil(n + 1).W))

    val flush = Input(Bool())
  })
  dontTouch(io)

  // Note first assert below should be sufficient it allows enqueueing items when buffer
  // is full or close to full provided deqReady >= enqValid.
  // The second assert is more conservative, and will never allow enqueueing more items
  // than there is space in the buffer, under any circumstances. May be removed if
  // desire for more is greater than the need to be more conservative.
  assert(io.nEnqueued +& io.enqValid -& io.deqReady <= capacity.U)
  assert(io.enqValid <= (capacity.U -& io.nEnqueued))

  assert(io.deqReady <= io.nEnqueued)

  val buffer = RegInit(VecInit.fill(capacity)(0.U.asTypeOf(t)))
  val enqPtr = RegInit(0.U(log2Ceil(capacity).W))
  val deqPtr = RegInit(0.U(log2Ceil(capacity).W))

  val expandedInput = Wire(Vec(capacity, Valid(t)))
  for (i <- 0 until capacity) {
    if (i < n) {
      expandedInput(i) := MakeValid(i.U < io.enqValid, io.enqData(i))
    } else {
      expandedInput(i) := MakeInvalid(t)
    }
  }

  val rotatedInput = RotateVectorLeft(expandedInput, enqPtr)
  for (i <- 0 until capacity) {
    buffer(i) := Mux(rotatedInput(i).valid, rotatedInput(i).bits, buffer(i))
  }


  var nEnqueued = RegInit(0.U(io.nEnqueued.getWidth.W))
  when(io.flush) {
    enqPtr := 0.U
    deqPtr := 0.U
    nEnqueued := 0.U
  } .otherwise {
    enqPtr := enqPtr + io.enqValid
    deqPtr := deqPtr + io.deqReady
    nEnqueued := nEnqueued + io.enqValid - io.deqReady
  }

  io.nEnqueued := nEnqueued
  io.nSpace := capacity.U - nEnqueued

  val outputBufferView = RotateVectorRight(buffer, deqPtr)
  for (i <- 0 until n) {
    io.dataOut(i) := outputBufferView(i)
  }
}