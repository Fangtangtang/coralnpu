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

#include <iostream>

#include "verilated_toplevel.h"
#include "verilator_memutil.h"
#include "verilator_sim_ctrl.h"

int main(int argc, char **argv) {
  chip_verilator top;
  VerilatorMemUtil memutil;
  VerilatorSimCtrl &simctrl = VerilatorSimCtrl::GetInstance();
  simctrl.SetTop(&top, &top.clk_i, &top.rst_ni,
                 VerilatorSimCtrlFlags::ResetPolarityNegative);

  // NB: Final parameter here is "width" of your memory, penultimate parameter
  // is "depth".
  MemArea rom("TOP.chip_verilator.i_kelvin_soc.i_rom.u_prim_rom", 0x8000 / 4,
              4);
  MemArea sram("TOP.chip_verilator.i_kelvin_soc.i_sram", 0x400000 / 4, 4);
  MemArea itcm(
      "TOP.chip_verilator.i_kelvin_soc.i_kelvin_core.coreAxi.itcm.sram."
      "sramModules_0",
      0x2000 / 16, 16);
  MemArea dtcm(
      "TOP.chip_verilator.i_kelvin_soc.i_kelvin_core.coreAxi.dtcm.sram."
      "sramModules_0",
      0x8000 / 16, 16);

  memutil.RegisterMemoryArea("rom", 0x10000000, &rom);
  memutil.RegisterMemoryArea("sram", 0x20000000, &sram);
  memutil.RegisterMemoryArea("itcm", 0x00000000, &itcm);
  memutil.RegisterMemoryArea("dtcm", 0x00010000, &dtcm);
  simctrl.RegisterExtension(&memutil);

  simctrl.SetInitialResetDelay(2000);
  simctrl.SetResetDuration(10);

  std::cout << "Simulation of Kelvin SoC" << std::endl
            << "======================" << std::endl
            << std::endl;

  return simctrl.Exec(argc, argv).first;
}
