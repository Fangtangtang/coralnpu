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

size_t vl __attribute__((section(".data"))) = 16;
size_t vtype __attribute__((section(".data"))) = 0;
size_t load_filler __attribute__((section(".data"))) = 0;
uint8_t mask_data[16] __attribute__((section(".data")));
uint8_t load_data[128] __attribute__((section(".data")));
// Allow load_addr to be overwritten to exercise AXI memory
uint8_t* load_addr __attribute__((section(".data"))) = load_data;
uint8_t store_data[128] __attribute__((section(".data")));

extern "C" {
__attribute__((used, retain)) void test_unit_load8() {
  asm("vsetvl zero, %[vl], %[vtype];"
      "vlm.v v0, %[mask_data];"
      "vmv.v.x v8, %[load_filler];"
      "vle8.v v8, %[load_addr], v0.t;"
      "vse8.v v8, %[store_data];"
      : [store_data] "=m"(store_data)
      : [vl] "r"(vl),
        [vtype] "r"(vtype),
        [load_filler] "r"(load_filler),
        [mask_data] "m"(mask_data),
        [load_addr] "m"(*reinterpret_cast<const uint8_t(*)[128]>(load_addr))
      : "v0", "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15",
        "vl", "vtype");
}

__attribute__((used, retain)) void test_unit_load16() {
  asm("vsetvl zero, %[vl], %[vtype];"
      "vlm.v v0, %[mask_data];"
      "vmv.v.x v8, %[load_filler];"
      "vle16.v v8, %[load_addr], v0.t;"
      "vse16.v v8, %[store_data];"
      : [store_data] "=m"(store_data)
      : [vl] "r"(vl),
        [vtype] "r"(vtype),
        [load_filler] "r"(load_filler),
        [mask_data] "m"(mask_data),
        [load_addr] "m"(*reinterpret_cast<const uint16_t(*)[64]>(load_addr))
      : "v0", "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15",
        "vl", "vtype");
}

__attribute__((used, retain)) void test_unit_load32() {
  asm("vsetvl zero, %[vl], %[vtype];"
      "vlm.v v0, %[mask_data];"
      "vmv.v.x v8, %[load_filler];"
      "vle32.v v8, %[load_addr], v0.t;"
      "vse32.v v8, %[store_data];"
      : [store_data] "=m"(store_data)
      : [vl] "r"(vl),
        [vtype] "r"(vtype),
        [load_filler] "r"(load_filler),
        [mask_data] "m"(mask_data),
        [load_addr] "m"(*reinterpret_cast<const uint32_t(*)[32]>(load_addr))
      : "v0", "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15",
        "vl", "vtype");
}
}

void (*impl)() __attribute__((section(".data"))) = &test_unit_load8;

int main(int argc, char** argv) {
  impl();

  return 0;
}
