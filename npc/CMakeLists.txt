cmake_minimum_required(VERSION 3.20)

project(npc LANGUAGES CXX)

set(NPC_WAVE "fst" CACHE STRING "Waveform support: fst or none")
set_property(CACHE NPC_WAVE PROPERTY STRINGS fst none)

find_package(CLI11 CONFIG REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(CAPSTONE REQUIRED IMPORTED_TARGET capstone)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(VERILATOR_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/verilator")
set(VERILATOR_MODEL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/build/obj_dir")

file(GLOB_RECURSE NPC_VERILATOR_SOURCES CONFIGURE_DEPENDS
  "${VERILATOR_SOURCE_DIR}/*.cc"
)

list(FILTER NPC_VERILATOR_SOURCES EXCLUDE REGEX "/wave/.*\\.cc$")

if(NPC_WAVE STREQUAL "fst")
  list(APPEND NPC_VERILATOR_SOURCES "${VERILATOR_SOURCE_DIR}/wave/fst.cc")
  set(NPC_VM_TRACE 1)
  set(NPC_VM_TRACE_FST 1)
  set(NPC_VM_TRACE_VCD 0)
elseif(NPC_WAVE STREQUAL "none")
  list(APPEND NPC_VERILATOR_SOURCES "${VERILATOR_SOURCE_DIR}/wave/none.cc")
  set(NPC_VM_TRACE 0)
  set(NPC_VM_TRACE_FST 0)
  set(NPC_VM_TRACE_VCD 0)
else()
  message(FATAL_ERROR "NPC_WAVE must be one of: fst, none")
endif()

add_library(npc_verilator OBJECT EXCLUDE_FROM_ALL ${NPC_VERILATOR_SOURCES})

target_compile_features(npc_verilator PRIVATE cxx_std_23)
set_target_properties(npc_verilator PROPERTIES CXX_EXTENSIONS OFF)

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
  VM_TRACE=${NPC_VM_TRACE}
  VM_TRACE_FST=${NPC_VM_TRACE_FST}
  VM_TRACE_VCD=${NPC_VM_TRACE_VCD}
  VM_TRACE_SAIF=0
  VM_VPI=0
)

if(NOT EXISTS "${VERILATOR_MODEL_DIR}/VCore.h")
  message(STATUS "Run `make verilator-build` to generate VCore.h before building npc_verilator.")
endif()
