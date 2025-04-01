#!/bin/sh

mkdir -p rv32_out
TOOLCHAIN_OUT_DIR="$(pwd)"/rv32_out

# Build gcc
git clone --branch 2025.01.20 https://github.com/riscv-collab/riscv-gnu-toolchain.git
cd riscv-gnu-toolchain/ || exit
git submodule update --init
git submodule update gcc

# Update gcc
cd gcc || exit
git fetch
git checkout origin/master
cd .. || exit

./configure \
  --srcdir="$(pwd)" \
  --prefix="${TOOLCHAIN_OUT_DIR}" \
  --with-arch=rv32im_zve32x_zicsr \
  --with-abi=ilp32 \
  --with-gcc-src="$(pwd)"/gcc \
  --with-cmodel=medany

make -C "$(pwd)" -j 32  newlib
cd .. || exit

# Build LLVM + compiler-rt
git clone --depth=1 https://github.com/llvm/llvm-project -b llvmorg-19.1.7
cd llvm-project/ || exit

# Build LLVM
mkdir build
cmake -B build \
    -DCMAKE_INSTALL_PREFIX="${TOOLCHAIN_OUT_DIR}" \
    -DCMAKE_C_COMPILER=clang \
    -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DLLVM_TARGETS_TO_BUILD="RISCV" \
    -DLLVM_ENABLE_PROJECTS="clang"  \
    -DLLVM_DEFAULT_TARGET_TRIPLE="riscv32-unknown-elf" \
    -DLLVM_INSTALL_TOOLCHAIN_ONLY=On \
    -DDEFAULT_SYSROOT="${TOOLCHAIN_OUT_DIR}"/riscv32-unknown-elf \
    -G Ninja \
    "$(pwd)"/llvm

cmake --build  build --target install

cmake -B "$(pwd)"/build/compiler-rt \
    -DCMAKE_INSTALL_PREFIX="${TOOLCHAIN_OUT_DIR}" \
    -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY \
    -DCMAKE_AR="${TOOLCHAIN_OUT_DIR}"/bin/llvm-ar \
    -DCMAKE_NM="${TOOLCHAIN_OUT_DIR}"/bin/llvm-nm \
    -DCMAKE_RANLIB="${TOOLCHAIN_OUT_DIR}"/bin/llvm-ranlib \
    -DCMAKE_C_FLAGS="-march=rv32im_zve32x_zicsr" \
    -DCMAKE_ASM_FLAGS="-march=rv32im_zve32x_zicsr" \
    -DCMAKE_C_COMPILER="${TOOLCHAIN_OUT_DIR}"/bin/clang \
    -DCMAKE_C_COMPILER_TARGET=riscv32-unknown-elf \
    -DCMAKE_ASM_COMPILER_TARGET=riscv32-unknown-elf \
    -DCOMPILER_RT_OS_DIR="clang/14.0.0/lib" \
    -DCMAKE_EXE_LINKER_FLAGS="-fuse-ld=lld" \
    -DCOMPILER_RT_BUILD_BUILTINS=ON \
    -DCOMPILER_RT_BUILD_SANITIZERS=OFF \
    -DCOMPILER_RT_BUILD_XRAY=OFF \
    -DCOMPILER_RT_BUILD_LIBFUZZER=OFF \
    -DCOMPILER_RT_BUILD_MEMPROF=OFF \
    -DCOMPILER_RT_BUILD_PROFILE=OFF \
    -DCOMPILER_RT_BAREMETAL_BUILD=ON \
    -DCOMPILER_RT_DEFAULT_TARGET_ONLY=ON \
    -DLLVM_CONFIG_PATH="$(pwd)"/build/bin/llvm-config \
    -DCMAKE_C_FLAGS="-march=rv32im_zve32x_zicsr -mno-relax" \
    -DCMAKE_ASM_FLAGS="-march=rv32im_zve32x_zicsr -mno-relax" \
    -G "Ninja" "$(pwd)"/compiler-rt

cmake --build "$(pwd)"/build/compiler-rt --target install

cd .. || exit

# New lib build fails if riscv32-unknown-elf-gcc is not available in PATH
export PATH="${TOOLCHAIN_OUT_DIR}"/bin:"$PATH"

if [ ! -f "${TOOLCHAIN_OUT_DIR}"/bin/riscv32-unknown-elf-gcc ]; then
  echo "ERROR: ${TOOLCHAIN_OUT_DIR}/bin/riscv32-unknown-elf-gcc does not exits"
  exit 1
fi

# Build newlib
git clone --depth=1 https://github.com/riscvarchive/riscv-newlib -b newlib-4.1.0
cd riscv-newlib/ || exit
./configure \
  --target=riscv32-unknown-elf \
  --prefix="${TOOLCHAIN_OUT_DIR}" \
  --enable-newlib-io-long-double \
  --enable-newlib-io-long-long \
  --enable-newlib-io-float \
  --enable-newlib-io-c99-formats \
  --enable-newlib-register-fini \
  CC_FOR_TARGET=riscv32-unknown-elf-gcc \
  CXX_FOR_TARGET=riscv32-unknown-elf-g++ \
  CFLAGS_FOR_TARGET="-march=rv32im_zve32x_zicsr -O2 -D_POSIX_MODE -mno-relax -Wno-error=implicit-function-declaration -Wno-error=int-conversion" \
  CXXFLAGS_FOR_TARGET="-march=rv32im_zve32x_zicsr -O2 -D_POSIX_MODE -mno-relax -Wno-error=implicit-function-declaration -Wno-error=int-conversion"
make -j 32
make install
cd .. || exit