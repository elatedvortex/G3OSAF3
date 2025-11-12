package com.example.geosafe3.model

import com.example.geosafe3.R

enum class CrimeType(val icon: Int) {
    THEFT(R.drawable.ic_robber),
    HIT_AND_RUN(R.drawable.ic_car),
    MURDER(R.drawable.ic_knife),
    OTHER(R.drawable.ic_warning)
}
