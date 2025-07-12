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

#include <riscv_vector.h>
#include <stdint.h>

// Enough space for m2.
uint8_t in_buf[64] __attribute__((section(".data")));
uint8_t out_buf[64] __attribute__((section(".data")));

__attribute__((used, retain)) void test_intrinsic(const uint8_t *x,
                                                  uint8_t *y) {
  vuint8m1x2_t v = __riscv_vlseg2e8_v_u8m1x2(in_buf, 32);

  vuint8m2_t vv = __riscv_vcreate_v_u8m1_u8m2(
      __riscv_vget_v_u8m1x2_u8m1(v, 0),
      __riscv_vget_v_u8m1x2_u8m1(v, 1));

  __riscv_vse8_v_u8m2(y, vv, /*vl=*/32);
}

int main(int argc, char **argv) {
  test_intrinsic(in_buf, out_buf);
  return 0;
}
