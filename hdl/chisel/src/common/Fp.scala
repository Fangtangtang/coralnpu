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

package common

import chisel3._
import chisel3.util._

class Fp32 extends Bundle {
  val mantissa = UInt(23.W)
  val exponent = UInt(8.W)
  val sign     = Bool()

  def asWord(): UInt = {
    Cat(sign, exponent, mantissa)
  }

  def isZero(): Bool = {
    (exponent === 0.U) && (mantissa === 0.U)
  }

  def isInf(): Bool = {
    (exponent === "b11111111".U) && (mantissa === 0.U)
  }

  def isNan(): Bool = {
    (exponent === "b11111111".U) && (mantissa =/= 0.U)
  }

  // The mantissa is the fractional component of significand.
  // There is a leading 1, except for subnormal numbers.
  def significand(): UInt = {
    Cat(exponent.orR, mantissa)
  }

  def negate(): Fp32 = {
    val negated = Wire(new Fp32)
    negated.sign := ~sign
    negated.exponent := exponent
    negated.mantissa := mantissa
    negated
  }
}

object Fp32 {
  def apply(sign: Bool, exponent: UInt, mantissa: UInt): Fp32 = {
    assert(exponent.getWidth == 8)
    assert(mantissa.getWidth == 23)
    val fp = Wire(new Fp32)
    fp.mantissa := mantissa
    fp.exponent := exponent
    fp.sign := sign
    fp
  }

  def fromWord(word: UInt): Fp32 = {
    assert(word.getWidth == 32)
    val fp = Wire(new Fp32)
    fp.mantissa := word(22, 0)
    fp.exponent := word(30, 23)
    fp.sign := word(31)
    fp
  }

  def Zero(sign: Bool): Fp32 = {
    val fp = Wire(new Fp32)
    fp.mantissa := 0.U
    fp.exponent := 0.U
    fp.sign := sign
    fp
  }

  def Inf(sign: Bool): Fp32 = {
    val fp = Wire(new Fp32)
    fp.mantissa := 0.U
    fp.exponent := "b11111111".U
    fp.sign := sign
    fp
  }

  def NaN(): Fp32 = {
    val fp = Wire(new Fp32)
    fp.mantissa := (1 << 22).U  // Cannonical NaN per RV32F spec
    fp.exponent := "b11111111".U
    fp.sign := 0.U
    fp
  }
}