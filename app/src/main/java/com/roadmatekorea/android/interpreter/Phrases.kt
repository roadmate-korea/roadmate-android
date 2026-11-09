package com.roadmatekorea.android.interpreter

import androidx.annotation.StringRes
import com.roadmatekorea.android.R

/** 통역 상황. 상황에 따라 미리 준비한 문장과 서버 번역의 맥락이 달라진다. */
enum class Situation { GAS_STATION, PARKING, ACCIDENT, RENTAL }

/** 미리 번역해 둔 문장: 운전자 언어 문장과 한국어 문장. 연결이 없어도 쓸 수 있다. */
data class Phrase(val id: String, val situation: Situation, @StringRes val driverText: Int, @StringRes val korean: Int)

object Phrases {
    val all: List<Phrase> = listOf(
        Phrase("gas_full", Situation.GAS_STATION, R.string.phrase_gas_full, R.string.phrase_gas_full_ko),
        Phrase("gas_diesel", Situation.GAS_STATION, R.string.phrase_gas_diesel, R.string.phrase_gas_diesel_ko),
        Phrase("gas_amount", Situation.GAS_STATION, R.string.phrase_gas_amount, R.string.phrase_gas_amount_ko),
        Phrase("gas_card", Situation.GAS_STATION, R.string.phrase_gas_card, R.string.phrase_gas_card_ko),
        Phrase("park_here", Situation.PARKING, R.string.phrase_park_here, R.string.phrase_park_here_ko),
        Phrase("park_fee", Situation.PARKING, R.string.phrase_park_fee, R.string.phrase_park_fee_ko),
        Phrase("park_pay", Situation.PARKING, R.string.phrase_park_pay, R.string.phrase_park_pay_ko),
        Phrase("acc_help", Situation.ACCIDENT, R.string.phrase_acc_help, R.string.phrase_acc_help_ko),
        Phrase("acc_police", Situation.ACCIDENT, R.string.phrase_acc_police, R.string.phrase_acc_police_ko),
        Phrase("acc_hurt", Situation.ACCIDENT, R.string.phrase_acc_hurt, R.string.phrase_acc_hurt_ko),
        Phrase("acc_rental", Situation.ACCIDENT, R.string.phrase_acc_rental, R.string.phrase_acc_rental_ko),
        Phrase("rent_return", Situation.RENTAL, R.string.phrase_rent_return, R.string.phrase_rent_return_ko),
        Phrase("rent_time", Situation.RENTAL, R.string.phrase_rent_time, R.string.phrase_rent_time_ko),
        Phrase("rent_fuel", Situation.RENTAL, R.string.phrase_rent_fuel, R.string.phrase_rent_fuel_ko),
        Phrase("rent_problem", Situation.RENTAL, R.string.phrase_rent_problem, R.string.phrase_rent_problem_ko),
    )

    fun of(situation: Situation): List<Phrase> = all.filter { it.situation == situation }
}
