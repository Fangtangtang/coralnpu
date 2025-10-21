`verilator_config
// Typical Chisel-defined IO
public -module "{HDL_TOPLEVEL}" -var "io_*"

// Common clock names
public -module "{HDL_TOPLEVEL}" -var "clock"
public -module "{HDL_TOPLEVEL}" -var "clk"

// Common reset names
public -module "{HDL_TOPLEVEL}" -var "reset"
public -module "{HDL_TOPLEVEL}" -var "rst"
public -module "{HDL_TOPLEVEL}" -var "rst_ni"