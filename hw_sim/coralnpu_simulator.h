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

#ifndef HW_SIM_CORALNPU_SIMULATOR_H_
#define HW_SIM_CORALNPU_SIMULATOR_H_

#include "hw_sim/mailbox.h"

class CoralNPUSimulator {
 public:
  static CoralNPUSimulator* Create();

  virtual ~CoralNPUSimulator() = default;

  // Functions for reading/writing TCMs and Mailbox.
  virtual void ReadTCM(uint32_t addr, size_t size, char* data) = 0;
  virtual const CoralNPUMailbox& ReadMailbox(void) = 0;
  virtual void WriteTCM(uint32_t addr, size_t size, const char* data) = 0;
  virtual void WriteMailbox(const CoralNPUMailbox& mailbox) = 0;

  // Wait for interrupt
  virtual bool WaitForTermination(int timeout) = 0;

  // Begin executing starting with the PC set to the specified address. Returns
  // when the core halts.
  virtual void Run(uint32_t start_addr) = 0;
};

#endif  // HW_SIM_CORALNPU_SIMULATOR_H_
