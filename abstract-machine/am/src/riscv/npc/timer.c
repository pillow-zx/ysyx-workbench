#include <am.h>
#include <riscv/riscv.h>
#include "mmio.h"

#define RTC_MTIME_FREQ 50000000ULL
#define RTC_ADDR_LOW RTC_ADDR
#define RTC_ADDR_HIGH (RTC_ADDR + 4)

static uint64_t mtime_to_us(uint64_t ticks)
{
    uint64_t sec = ticks / RTC_MTIME_FREQ;
    uint64_t rem = ticks % RTC_MTIME_FREQ;

    return sec * 1000000ULL +
           rem * 1000000ULL / RTC_MTIME_FREQ;
}

void __am_timer_init()
{}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime)
{
        uint32_t low, high1, high2;
        uint64_t ticks;

        do {
                high1 = inl(RTC_ADDR_HIGH);
                low = inl(RTC_ADDR_LOW);
                high2 = inl(RTC_ADDR_HIGH);
        } while (high1 != high2);


        ticks = ((uint64_t)high1 << 32) | low;

        uptime->us = mtime_to_us(ticks);
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc)
{
        rtc->second = 0;
        rtc->minute = 0;
        rtc->hour = 0;
        rtc->day = 0;
        rtc->month = 0;
        rtc->year = 1900;
}
