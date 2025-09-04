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

uint32_t vma __attribute__((section(".data"))) = 0;
uint32_t vta __attribute__((section(".data"))) = 0;
uint32_t sew __attribute__((section(".data"))) = 0;
uint32_t lmul __attribute__((section(".data"))) = 0;
uint32_t vl __attribute__((section(".data"))) = 16;
uint32_t vtype __attribute__((section(".data"))) = ~0;

int main(int argc, char **argv) {
  uint32_t vtype_to_write = (vma << 7) | (vta << 6) | (sew << 3) | lmul;
  asm volatile("vsetvl x0, %0, %1": : "r"(vl), "r"(vtype_to_write));
  uint32_t vtype_to_read;
  asm volatile("csrr %0, vtype" : "=r"(vtype_to_read));
  vtype = vtype_to_read;
  return 0;
}
