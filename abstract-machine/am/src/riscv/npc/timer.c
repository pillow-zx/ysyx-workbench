#include <am.h>
#include <riscv/riscv.h>
#include "mmio.h"

#define RTC_ADDR_LOW RTC_ADDR
#define RTC_ADDR_HIGH (RTC_ADDR + 4)

void __am_timer_init()
{}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime)
{
        uint32_t low, high1, high2;

        do {
                high1 = inl(RTC_ADDR_HIGH);
                low = inl(RTC_ADDR_LOW);
                high2 = inl(RTC_ADDR_HIGH);
        }while (high1 != high2);

        uptime->us = ((uint64_t)high1 << 32) | low;
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
