PROJECT := npc
TOP := ysyxSoCFull

VERILATOR ?= verilator
PYTHON ?= python3

BUILD_DIR := build
OBJ_DIR := $(BUILD_DIR)/obj_dir
BIN := $(OBJ_DIR)/V$(TOP)
FILELIST := $(BUILD_DIR)/filelist.f
BUILD_SIG := $(BUILD_DIR)/.verilator-build-signature

JOBS ?= 0

ARGS ?=
DEFCONFIG ?=

SIM_DIR := src/verilator
SIM_INC_DIR := $(SIM_DIR)/include
WAVE_DIR := $(SIM_DIR)/wave
CONFIG_DIR := $(SIM_DIR)/config

YSYXSOC_DIR := $(YSYXSOC_HOME)

AUTOCONFIG_SCRIPT := $(CONFIG_DIR)/generate_config.py
AUTOCONFIG_HEADER := $(SIM_INC_DIR)/generated/autoconfig.hh

WAVE ?= $(if $(filter perf perf.toml,$(DEFCONFIG)),none,fst)
VALID_WAVES := fst none

ifeq ($(filter $(WAVE),$(VALID_WAVES)),)
$(error Invalid WAVE='$(WAVE)', expected one of: $(VALID_WAVES))
endif

ifeq ($(WAVE),fst)
WAVE_SRC := $(WAVE_DIR)/fst.cc
VERILATOR_TRACE_FLAGS := --trace-fst
else
WAVE_SRC := $(WAVE_DIR)/none.cc
VERILATOR_TRACE_FLAGS :=
endif

NPC_SRCS := $(shell find src -type f -name '*.scala' | sort)

YSYXSOC_PERIP_SRCS := $(shell find $(YSYXSOC_DIR)/perip -type f -name '*.v' | sort)
YSYXSOC_TOP := $(YSYXSOC_DIR)/build/ysyxSoCFull.v
YSYXSOC_SRCS := $(YSYXSOC_PERIP_SRCS) $(YSYXSOC_TOP)

SIM_COMMON_SRCS := $(shell find $(SIM_DIR) -type f -name '*.cc' ! -path '$(WAVE_DIR)/*' | sort)
SIM_SRCS := $(SIM_COMMON_SRCS) $(WAVE_SRC)

CXX_INC_DIRS := $(abspath $(SIM_INC_DIR))
RTL_INC_DIRS := $(YSYXSOC_DIR)/perip/uart16550/rtl $(YSYXSOC_DIR)/perip/spi/rtl

CXX_INC_FLAGS := $(addprefix -I,$(CXX_INC_DIRS))
RTL_INC_FLAGS := $(addprefix -I,$(RTL_INC_DIRS))

CAPSTONE_CFLAGS := $(shell pkg-config --cflags capstone)
CAPSTONE_LIBS := $(shell pkg-config --libs capstone)

CXXFLAGS := -std=c++23 -O3 -pthread
CXXFLAGS += $(CXX_INC_FLAGS)
CXXFLAGS += $(CAPSTONE_CFLAGS)

LDFLAGS := $(CAPSTONE_LIBS)

VERILATOR_BUILD_FLAGS := --cc --exe --build -j $(JOBS)
VERILATOR_INPUT_FLAGS := --top-module $(TOP) -F $(FILELIST) $(RTL_INC_FLAGS)
VERILATOR_TIMING_FLAGS := --timescale 1ns/1ns --no-timing
VERILATOR_X_FLAGS := --x-assign fast --x-initial fast
VERILATOR_OPT_FLAGS := -O3 --noassert
VERILATOR_DEP_FLAGS := -MMD
VERILATOR_OUTPUT_FLAGS := --output-split 20000 --output-split-cfuncs 10000 --Mdir $(OBJ_DIR)
VERILATOR_FLUSH_FLAGS := --autoflush

VERILATOR_FLAGS := $(VERILATOR_BUILD_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_INPUT_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_TIMING_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_X_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_OPT_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_DEP_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_OUTPUT_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_TRACE_FLAGS)
VERILATOR_FLAGS += $(VERILATOR_FLUSH_FLAGS)

BUILD_OPTIONS := $(VERILATOR_FLAGS) | $(CXXFLAGS) | $(LDFLAGS)

.PHONY: default all

default: all

all: sim

.PHONY: verilog

verilog: $(FILELIST)

$(FILELIST): $(NPC_SRCS) build.mill $(YSYXSOC_SRCS) | $(BUILD_DIR)
	$(call git_commit, "generate verilog")
	mill -i runMain npc.Elaborate --target-dir $(BUILD_DIR)
	@printf '%s\n' $(YSYXSOC_SRCS) >> $@

.PHONY: autoconfig

autoconfig: $(AUTOCONFIG_HEADER)

$(AUTOCONFIG_HEADER): FORCE
	$(Q)$(PYTHON) $(AUTOCONFIG_SCRIPT) --defconfig "$(DEFCONFIG)"

$(BUILD_DIR):
	$(Q)@mkdir -p $@

$(BUILD_SIG): FORCE | $(BUILD_DIR)
	$(Q)printf '%s\n' '$(BUILD_OPTIONS)' > $@.tmp
	$(Q)if cmp -s $@.tmp $@; then rm -f $@.tmp; else mv $@.tmp $@; fi

.PHONY: verilator-build

verilator-build: $(BIN)

$(BIN): $(FILELIST) $(SIM_SRCS) $(AUTOCONFIG_HEADER) $(BUILD_SIG) Makefile
	$(VERILATOR) $(VERILATOR_FLAGS) $(SIM_SRCS) -CFLAGS "$(CXXFLAGS)" -LDFLAGS "$(LDFLAGS)"

.PHONY: sim run

sim: verilator-build
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	echo "Running $(BIN) $(ARGS)"
	$(Q)$(BIN) $(ARGS)

run: verilator-build
	$(quiet_run)
	$(BIN) $(ARGS)

.PHONY: reformat bsp idea

reformat:
	mill -i __.reformat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

.PHONY: clean FORCE

clean:
	rm -rf $(BUILD_DIR)

FORCE:

-include ../Makefile
