.section entry, "ax"
.globl _start
.type _start, @function

_start:
    mv      s0,   zero

    # 初始化全局栈
    la      sp,   _stack_pointer

    # 复制 .data 到 sram
    la      t0, _data_load_start
    la      t1, _data_start   
    la      t2, _data_end

1:
    beq     t1, t2, 2f
    lw      a0, 0(t0)
    sw      a0, 0(t1)
    addi    t0, t0, 4
    addi    t1, t1, 4
    j       1b

    # clear bss
2:
    la      t0, _bss_start
    la      t1, _bss_end

3:
    beq     t0, t1, 4f
    sw      zero, 0(t0)
    addi    t0, t0, 4
    j       3b

    # enter c runtime
4:
    call _trm_init

.size _start, . - _start
