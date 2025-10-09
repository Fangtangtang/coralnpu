import cocotb
import itertools
import numpy as np
import tqdm
from coralnpu_test_utils.core_mini_axi_interface import CoreMiniAxiInterface
from coralnpu_test_utils.sim_test_fixture import Fixture
from bazel_tools.tools.python.runfiles import runfiles

SEWS = [
    0b000,  # SEW8
    0b001,  # SEW16
    0b010,  # SEW32
]

# See 3.4.2. Vector Register Grouping of RVV Spec
LMULS = [
    0b100,  # Reserved
    0b101,  # LMUL1/8
    0b110,  # LMUL1/4
    0b111,  # LMUL1/2
    0b000,  # LMUL1
    0b001,  # LMUL2
    0b010,  # LMUL4
    0b011,  # LMUL8
]

def _illegal_vtype(sew, lmul):
    # SEW must be SEW8,16,32. Others are illegal
    if not ((sew == 0b000) or (sew == 0b001) or (sew == 0b010)):
      return True

    # Reserved or LMUL=1/8 always illegal
    if (lmul == 0b100) or (lmul == 0b101):
      return True

    # LMUL=1/4 is illegal for SEW16 and SEW32
    if (sew != 0b000) and (lmul == 0b110):
      return True

    # LMUL=1/2 is illegal for SEW32
    if (sew == 0b010) and (lmul == 0b111):
      return True

    return False


@cocotb.test()
async def core_mini_rvv_load(dut):
    """Testbench to test RVV load intrinsics.

    This test loads 16 bytes of data and read back from the input address.
    Todo: update the test with store unit.
    """
    # Test bench setup
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/rvv_load.elf")
    num_test_bytes = 16
    intial_pass = True
    if not elf_path:
        raise ValueError("elf_path must consist a valid path")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)

    #Write your program inputs
    with open(elf_path, "rb") as f:
        input_1_addr = core_mini_axi.lookup_symbol(f, "input_1")
        output_1_addr = core_mini_axi.lookup_symbol(f, "output_1")

    for data_type in [np.int8, np.int16, np.int32]:

        num_bytes = np.dtype(data_type).itemsize
        min_value = np.iinfo(data_type).min
        max_value = np.iinfo(data_type).max
        num_values = int(num_test_bytes / num_bytes)
        input_1_data = np.random.randint(min_value, max_value, num_values, dtype=data_type)
        await core_mini_axi.write(input_1_addr, input_1_data)
        if intial_pass:
            intial_pass = False
            await core_mini_axi.execute_from(entry_point)

        await core_mini_axi.wait_for_wfi()
        routputs = (await core_mini_axi.read(input_1_addr, num_test_bytes)).view(data_type)
        print(f"loaded inputs are {routputs}", flush=True)
        print(f" number of values supposed to be printed {num_values}", flush=True)
        await core_mini_axi.raise_irq()
    await core_mini_axi.wait_for_halted()


@cocotb.test()
async def core_mini_rvv_add(dut):
    """Testbench to test RVV add intrinsics.

    This test loads 16 bytes of data from each input buffer and saved result into a register.

    Todo: update the test with store unit.
    """
    # Test bench setup
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/rvv_add.elf")
    num_test_bytes = 16
    intial_pass = True

    if not elf_path:
      raise ValueError("elf_path must consist a valid path ")
    with open(elf_path, "rb") as f:
      entry_point = await core_mini_axi.load_elf(f)

    #Write your program inputs
    with open(elf_path, "rb") as f:
      input_1_addr = core_mini_axi.lookup_symbol(f, "input_1")
      input_2_addr = core_mini_axi.lookup_symbol(f, "input_2")
      output_1_addr = core_mini_axi.lookup_symbol(f, "output_1")

    # todo ,np.uint8, np.uint16, np.uint32
    for data_type in [np.int8, np.int16, np.int32]:

        num_bytes = np.dtype(data_type).itemsize
        min_value = np.iinfo(data_type).min
        max_value = np.iinfo(data_type).max
        num_values = int(num_test_bytes / num_bytes)
        input_1_data = np.random.randint(min_value, max_value, num_values, dtype=data_type)
        input_2_data = np.random.randint(min_value, max_value, num_values, dtype=data_type)

        await core_mini_axi.write(input_1_addr, input_1_data)
        if intial_pass:
            intial_pass = False
            await core_mini_axi.execute_from(entry_point)
        await core_mini_axi.wait_for_wfi()
        routputs = (await core_mini_axi.read(input_1_addr, num_test_bytes)).view(data_type)
        print(f"loaded inputs are {routputs}", flush=True)
        routputs2 = (await core_mini_axi.read(input_1_addr, num_test_bytes)).view(data_type)
        print(f"loaded inputs are {routputs2}", flush=True)
        print(f" number of values supposed to be printed {num_values}", flush=True)
        await core_mini_axi.raise_irq()
    await core_mini_axi.wait_for_halted()


@cocotb.test()
async def core_mini_vstart_store(dut):
    """Testbench to test vstart store.
    """
    # Test bench setup
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/vstart_store.elf")
    if not elf_path:
        raise ValueError("elf_path must consist a valid path")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)

    #Write your program inputs
    with open(elf_path, "rb") as f:
        input_addr = core_mini_axi.lookup_symbol(f, "input_data")
        output_addr = core_mini_axi.lookup_symbol(f, "output_data")

    input_data = np.random.randint(
        np.iinfo(np.uint8).min, np.iinfo(np.uint8).max, 16, dtype=np.uint8)
    await core_mini_axi.write(input_addr, input_data)
    await core_mini_axi.write(output_addr, np.zeros(16, dtype=np.uint8))

    await core_mini_axi.execute_from(entry_point)
    await core_mini_axi.wait_for_wfi()

    output_data = (await core_mini_axi.read(output_addr, 16)).view(np.uint8)

    # vstart is 4, so first 4 elements are skipped.
    # 12 elements are stored.
    assert np.array_equal(output_data[0:4], np.zeros(4, dtype=np.uint8))
    assert np.array_equal(output_data[4:], input_data[4:])

    await core_mini_axi.raise_irq()
    await core_mini_axi.wait_for_halted()


@cocotb.test()
async def core_mini_vcsr_test(dut):
    """Testbench to test vcsr is set correctly."""
    # Test bench setup
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/vcsr_test.elf")
    if not elf_path:
        raise ValueError("elf_path must consist a valid path")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)
        vma_addr = core_mini_axi.lookup_symbol(f, "vma")
        vta_addr = core_mini_axi.lookup_symbol(f, "vta")
        sew_addr = core_mini_axi.lookup_symbol(f, "sew")
        lmul_addr = core_mini_axi.lookup_symbol(f, "lmul")
        vl_addr = core_mini_axi.lookup_symbol(f, "vl")
        vtype_addr = core_mini_axi.lookup_symbol(f, "vtype")

    combined_loops = itertools.product(range(2), range(2), SEWS, LMULS)
    total_loops = 2 * 2 * len(SEWS) * len(LMULS)
    with tqdm.tqdm(combined_loops, total=total_loops) as t:
        for ma, ta, sew, lmul in t:
            t.set_postfix(
                {'ma': ma, 'ta': ta, 'sew': bin(sew), 'lmul': bin(lmul) })
            await core_mini_axi.write_word(vma_addr, ma)
            await core_mini_axi.write_word(vta_addr, ta)
            await core_mini_axi.write_word(sew_addr, sew)
            await core_mini_axi.write_word(lmul_addr, lmul)
            # TODO(derekjchow): Pick random VL
            await core_mini_axi.write_word(vl_addr, 1)

            await core_mini_axi.execute_from(entry_point)
            await core_mini_axi.wait_for_halted()

            vtype_result = (
                await core_mini_axi.read_word(vtype_addr)).view(np.uint32)[0]

            # Check if vtype is legal
            expected_illegal = _illegal_vtype(sew, lmul)
            result_illegal = (vtype_result & (1 << 31)) >> 31
            assert (expected_illegal == result_illegal)

            if expected_illegal:
                ma_result = (vtype_result & (1 << 7)) >> 7
                ta_result = (vtype_result & (1 << 6)) >> 6
                sew_result = (vtype_result & (0b111 << 3)) >> 3
                lmul_result = (vtype_result & 0b111)

                assert (ma == ma_result)
                assert (ta == ta_result)
                assert (sew == sew_result)
                assert (lmul == lmul_result)




async def test_vstart_not_zero_failure(dut, binary):
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation(binary)
    if not elf_path:
        raise ValueError("elf_path must consist a valid path")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)
        vma_addr = core_mini_axi.lookup_symbol(f, "vma")
        vta_addr = core_mini_axi.lookup_symbol(f, "vta")
        sew_addr = core_mini_axi.lookup_symbol(f, "sew")
        lmul_addr = core_mini_axi.lookup_symbol(f, "lmul")
        vl_addr = core_mini_axi.lookup_symbol(f, "vl")
        vstart_addr = core_mini_axi.lookup_symbol(f, "vstart")
        faulted_addr = core_mini_axi.lookup_symbol(f, "faulted")
        mcause_addr = core_mini_axi.lookup_symbol(f, "mcause")

    for ma in range(2):
      for ta in range(2):
        for sew in SEWS:
          for lmul in LMULS:
            vl = 4 # TODO(derekjchow): Pick random VL
            vstart = 1 # Non-zero to trigger failure

            await core_mini_axi.write_word(vma_addr, ma)
            await core_mini_axi.write_word(vta_addr, ta)
            await core_mini_axi.write_word(sew_addr, sew)
            await core_mini_axi.write_word(lmul_addr, lmul)
            await core_mini_axi.write_word(vl_addr, vl)
            await core_mini_axi.write_word(vstart_addr, vstart)

            await core_mini_axi.execute_from(entry_point)
            await core_mini_axi.wait_for_halted()

            faulted_result = (
                await core_mini_axi.read_word(faulted_addr)).view(np.uint32)[0]
            assert (faulted_result == 1)
            mcause_result = (
                await core_mini_axi.read_word(mcause_addr)).view(np.uint32)[0]
            assert (mcause_result == 0x2)


@cocotb.test()
async def core_mini_viota_test(dut):
    """Testbench to test vstart!=0 viota."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/viota_test.elf")


@cocotb.test()
async def core_mini_vfirst_test(dut):
    """Testbench to test vstart!=0 vfirst."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vfirst_test.elf")


@cocotb.test()
async def core_mini_vcpop_exception_test(dut):
    """Testbench to test vstart!=0 vcpop."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vcpop_exception_test.elf")


@cocotb.test()
async def core_mini_vcpop_test(dut):
    """Test vcpop usage accessible from intrinsics."""
    # mask is not accessible from here.
    fixture = await Fixture.Create(dut)
    r = runfiles.Create()
    cases = [
        {'impl': 'vcpop_m_b1', 'vl': 128},
        {'impl': 'vcpop_m_b1', 'vl': 121},
        {'impl': 'vcpop_m_b1', 'vl': 120},
        {'impl': 'vcpop_m_b2', 'vl': 64},
        {'impl': 'vcpop_m_b2', 'vl': 57},
        {'impl': 'vcpop_m_b2', 'vl': 56},
        {'impl': 'vcpop_m_b4', 'vl': 32},
        {'impl': 'vcpop_m_b4', 'vl': 25},
        {'impl': 'vcpop_m_b4', 'vl': 24},
        {'impl': 'vcpop_m_b8', 'vl': 16},
        {'impl': 'vcpop_m_b8', 'vl': 9},
        {'impl': 'vcpop_m_b8', 'vl': 8},
        {'impl': 'vcpop_m_b16', 'vl': 8},
        {'impl': 'vcpop_m_b16', 'vl': 1},
        {'impl': 'vcpop_m_b32', 'vl': 4},
        {'impl': 'vcpop_m_b32', 'vl': 1},
    ]
    await fixture.load_elf_and_lookup_symbols(
        r.Rlocation('coralnpu_hw/tests/cocotb/rvv/vcpop_test.elf'),
        ['vl', 'in_buf', 'result', 'impl'] + [c['impl'] for c in cases],
    )
    rng = np.random.default_rng()
    for c in cases:
        impl = c['impl']
        vl = c['vl']
        in_bytes = (vl + 7) // 8
        last_byte_mask = (1 << (vl % 8) - 1) if vl % 8 else 0xFF

        input_data = rng.integers(
            low=0, high=256, size=in_bytes, dtype=np.uint8)
        input_data_trimmed = input_data
        input_data_trimmed[-1] = input_data_trimmed[-1] & last_byte_mask
        expected_output = np.sum(
            np.bitwise_count(input_data), dtype=np.uint32)

        await fixture.write_ptr('impl', impl)
        await fixture.write_word('vl', vl)
        await fixture.write('in_buf', input_data)
        await fixture.write_word('result', 0)

        await fixture.run_to_halt()

        actual_output = (await fixture.read_word('result')).view(np.uint32)

        debug_msg = str({
            'impl': impl,
            'input': input_data,
            'expected': expected_output,
            'actual': actual_output,
        })
        assert (actual_output == expected_output), debug_msg


@cocotb.test()
async def core_mini_vcompress_test(dut):
    """Testbench to test vstart!=0 vcompress."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vcompress_test.elf")


@cocotb.test()
async def core_mini_vmsbf_test(dut):
    """Testbench to test vstart!=0 vmsbf."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vmsbf_test.elf")


@cocotb.test()
async def core_mini_vmsof_test(dut):
    """Testbench to test vstart!=0 vmsof."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vmsof_test.elf")


@cocotb.test()
async def core_mini_vmsif_test(dut):
    """Testbench to test vstart!=0 vmsbf."""
    await test_vstart_not_zero_failure(
        dut, "coralnpu_hw/tests/cocotb/rvv/vmsif_test.elf")


@cocotb.test()
async def core_mini_vill_test(dut):
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/vill_test.elf")
    if not elf_path:
        raise ValueError("elf_path must consist a valid path")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)
        faulted_addr = core_mini_axi.lookup_symbol(f, "faulted")
        mcause_addr = core_mini_axi.lookup_symbol(f, "mcause")

    await core_mini_axi.execute_from(entry_point)
    await core_mini_axi.wait_for_halted()

    faulted_result = (
        await core_mini_axi.read_word(faulted_addr)).view(np.uint32)[0]
    assert (faulted_result == 1)
    mcause_result = (
        await core_mini_axi.read_word(mcause_addr)).view(np.uint32)[0]
    assert (mcause_result == 0x2)

@cocotb.test()
async def core_mini_vl_test(dut):
    """Testbench to test vsetvl instruciton saturate vl correctly."""
    # Test bench setup
    core_mini_axi = CoreMiniAxiInterface(dut)
    await core_mini_axi.init()
    await core_mini_axi.reset()
    cocotb.start_soon(core_mini_axi.clock.start())
    r = runfiles.Create()

    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/vcsr_test.elf")
    with open(elf_path, "rb") as f:
        entry_point = await core_mini_axi.load_elf(f)
        sew_addr = core_mini_axi.lookup_symbol(f, "sew")
        lmul_addr = core_mini_axi.lookup_symbol(f, "lmul")
        vl_addr = core_mini_axi.lookup_symbol(f, "vl")
        vtype_addr = core_mini_axi.lookup_symbol(f, "vtype")
        result_vl_addr = core_mini_axi.lookup_symbol(f, "result_vl")

    cases = [
        (0b000, 0b110, 4),   # SEW8, mf4, vlmax=4
        (0b000, 0b111, 8),   # SEW8, mf2, vlmax=8
        (0b000, 0b000, 16),  # SEW8, m1, vlmax=16
        (0b000, 0b001, 32),  # SEW8, m2, vlmax=32
        (0b000, 0b010, 64),  # SEW8, m4, vlmax=64
        (0b000, 0b011, 128), # SEW8, m8, vlmax=128
        (0b001, 0b111, 4),   # SEW16, mf2, vlmax=4
        (0b001, 0b000, 8),   # SEW16, m1, vlmax=8
        (0b001, 0b001, 16),  # SEW16, m2, vlmax=16
        (0b001, 0b010, 32),  # SEW16, m4, vlmax=32
        (0b001, 0b011, 64),  # SEW16, m8, vlmax=64
        (0b010, 0b000, 4),   # SEW32, m1, vlmax=4
        (0b010, 0b001, 8),   # SEW32, m2, vlmax=8
        (0b010, 0b010, 16),  # SEW32, m4, vlmax=16
        (0b010, 0b011, 32),  # SEW32, m8, vlmax=32
    ]
    for sew, lmul, vlmax in tqdm.tqdm(cases):
        await core_mini_axi.write_word(sew_addr, sew)
        await core_mini_axi.write_word(lmul_addr, lmul)

        # Test saturation above vlmax
        vl_to_set = vlmax + 1
        await core_mini_axi.write_word(vl_addr, vl_to_set)
        await core_mini_axi.execute_from(entry_point)
        await core_mini_axi.wait_for_halted()
        vl_result = (
            await core_mini_axi.read_word(result_vl_addr)).view(np.uint32)[0]
        assert(vl_result == vlmax)

        # Test vlmax
        await core_mini_axi.write_word(vl_addr, vlmax)
        await core_mini_axi.execute_from(entry_point)
        await core_mini_axi.wait_for_halted()
        vl_result = (
            await core_mini_axi.read_word(result_vl_addr)).view(np.uint32)[0]
        assert(vl_result == vlmax)

        # Test below vlmax
        await core_mini_axi.write_word(vl_addr, vlmax - 1)
        await core_mini_axi.execute_from(entry_point)
        await core_mini_axi.wait_for_halted()
        vl_result = (
            await core_mini_axi.read_word(result_vl_addr)).view(np.uint32)[0]
        assert(vl_result == (vlmax - 1))