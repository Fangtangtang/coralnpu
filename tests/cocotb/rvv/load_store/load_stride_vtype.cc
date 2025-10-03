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

// Tests strided loads after setting vtype. Loads/stores should be agnostic to
// vtype.

size_t vl __attribute__((section(".data"))) = 16;
size_t vtype __attribute__((section(".data"))) = 0;
size_t stride __attribute__((section(".data"))) = 1;
uint8_t load_data[8192] __attribute__((section(".data")));
uint8_t store_data[256] __attribute__((section(".data")));

extern "C" {

#define CREATE_STRIDED_LOAD_FN(name, data_bits)                           \
  __attribute__((used, retain)) void name() {                             \
    size_t store_vl = 8 * __riscv_vlenb();                                \
    asm("vsetvl zero, %[vl], %[vtype];"                                    \
        "vlse" #data_bits ".v v8, %[load_data], %[stride];"                \
        "vsetvli zero, %[store_vl], e8, m8, ta, ma;"                       \
        "vse8.v v8, %[store_data];"                                       \
        : [store_data] "=m"(store_data)                                   \
        : [vl] "r"(vl), [store_vl] "r"(store_vl), [vtype] "r"(vtype),      \
          [stride] "r"(stride), [load_data] "m"(load_data)                \
        : "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15", "vl",      \
          "vtype");                                                       \
  }

#define CREATE_SEGMENT_STRIDED_LOAD_FN(name, data_bits, segment)            \
  __attribute__((used, retain)) void name() {                               \
    size_t store_vl = 8 * __riscv_vlenb();                                  \
    asm("vsetvl zero, %[vl], %[vtype];"                                      \
        "vlsseg" #segment "e" #data_bits ".v v8, %[load_data], %[stride];"   \
        "vsetvli zero, %[store_vl], e8, m8, ta, ma;"                         \
        "vse8.v v8, %[store_data];"                                         \
        : [store_data] "=m"(store_data)                                     \
        : [vl] "r"(vl), [store_vl] "r"(store_vl), [vtype] "r"(vtype),        \
          [stride] "r"(stride), [load_data] "m"(load_data)                  \
        : "v8", "v9", "v10", "v11", "v12", "v13", "v14", "v15", "vl",        \
          "vtype");                                                         \
  }

CREATE_STRIDED_LOAD_FN(test_vlse8, 8)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg2e8, 8, 2)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg3e8, 8, 3)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg4e8, 8, 4)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg5e8, 8, 5)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg6e8, 8, 6)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg7e8, 8, 7)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg8e8, 8, 8)

CREATE_STRIDED_LOAD_FN(test_vlse16, 16)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg2e16, 16, 2)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg3e16, 16, 3)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg4e16, 16, 4)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg5e16, 16, 5)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg6e16, 16, 6)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg7e16, 16, 7)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg8e16, 16, 8)

CREATE_STRIDED_LOAD_FN(test_vlse32, 32)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg2e32, 32, 2)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg3e32, 32, 3)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg4e32, 32, 4)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg5e32, 32, 5)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg6e32, 32, 6)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg7e32, 32, 7)
CREATE_SEGMENT_STRIDED_LOAD_FN(test_vlsseg8e32, 32, 8)
}

void (*impl)() __attribute__((section(".data"))) = &test_vlse8;

int main(int argc, char** argv) {
  impl();
  return 0;
}
