cmake_minimum_required(VERSION 3.20)

project(npc LANGUAGES CXX)

find_package(CLI11 CONFIG REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(CAPSTONE REQUIRED IMPORTED_TARGET capstone)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(VERILATOR_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/verilator")
set(VERILATOR_MODEL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/build/obj_dir")

file(GLOB_RECURSE NPC_VERILATOR_SOURCES CONFIGURE_DEPENDS
  "${VERILATOR_SOURCE_DIR}/*.cc"
)

add_library(npc_verilator OBJECT EXCLUDE_FROM_ALL ${NPC_VERILATOR_SOURCES})

target_include_directories(npc_verilator PRIVATE
  "${VERILATOR_SOURCE_DIR}/include"
  "${VERILATOR_MODEL_DIR}"
  "/usr/share/verilator/include"
  "/usr/share/verilator/include/vltstd"
)

target_link_libraries(npc_verilator PRIVATE
  CLI11::CLI11
  PkgConfig::CAPSTONE
)

target_compile_definitions(npc_verilator PRIVATE
  VERILATOR=1
  VM_COVERAGE=0
  VM_SC=0
  VM_TIMING=0
  VM_TRACE=1
  VM_TRACE_FST=0
  VM_TRACE_VCD=1
  VM_TRACE_SAIF=0
  VM_VPI=0
)

if(NOT EXISTS "${VERILATOR_MODEL_DIR}/VCore.h")
  message(STATUS "Run `make verilator-build` to generate VCore.h before building npc_verilator.")
endif()
