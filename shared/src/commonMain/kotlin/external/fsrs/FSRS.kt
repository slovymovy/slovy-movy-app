/*
 * Adapted from FSRS-Kotlin:
 * https://github.com/open-spaced-repetition/FSRS-Kotlin
 * Original FSRS author: Jarrett Ye.
 *
 * FSRS-Kotlin is provided under the MIT License. See THIRD_PARTY_NOTICES.md
 * in this directory for attribution and license details.
 */

package external.fsrs

import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class FSRS(
    private val requestRetention: Double,
    inputParams: List<Double>,
    private val enableFuzz: Boolean = false,
    private val fuzzFactorProvider: (FuzzContext) -> Double = { context -> defaultFuzzFactor(context) },
    private val maximumInterval: Int = 36500,
    private val isReview: Boolean = false,
) {
    data class InitState(var difficulty: Double = 0.0, var stability: Double = 0.0)

    data class FuzzContext(
        val seed: Long?,
        val rating: Rating,
        val interval: Int,
        val scheduledDays: Int,
    )

    private companion object {
        const val STABILITY_MIN = 0.001
        const val DIFFICULTY_MIN = 1.0
        const val DIFFICULTY_MAX = 10.0
        const val FIFTY_THREE_BITS: Long = 1L shl 53

        val AGAIN_DURATION: Duration = 3.minutes
        val FIVE_MINUTES: Duration = 5.minutes
        val TEN_MINUTES: Duration = 10.minutes
        val ONE_DAY: Duration = 1.days

        fun defaultFuzzFactor(context: FuzzContext): Double {
            val seed = context.seed ?: return 0.5
            var mixed = seed xor (context.rating.value.toLong() * -7046029254386353131L)
            mixed = mixed xor (context.interval.toLong() * -4417276706812531889L)
            mixed = mixed xor (context.scheduledDays.toLong() * -8796714831421723037L)
            mixed = splitMix64(mixed)
            return (mixed ushr 11).toDouble() / FIFTY_THREE_BITS.toDouble()
        }

        private fun splitMix64(value: Long): Long {
            var z = value + -7046029254386353131L
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }
    }

    private val params: List<Double>
    private val decay: Double
    private val factor: Double

    init {
        require(inputParams.size == 21) {
            "FSRS-6 requires exactly 21 parameters, got ${inputParams.size}"
        }
        require(requestRetention > 0.0 && requestRetention < 1.0) {
            "requestRetention must be in (0, 1), got $requestRetention"
        }
        require(maximumInterval >= 1) {
            "maximumInterval must be >= 1, got $maximumInterval"
        }

        params = inputParams.toList()
        decay = -params[20]
        factor = 0.9.pow(1.0 / decay) - 1.0
    }

    fun calculate(flashCard: FlashCard, fuzzSeed: Long? = null): List<Grade> {
        val stateAgain: InitState
        val stateHard: InitState
        val stateGood: InitState
        val stateEasy: InitState

        var durationHard = FIVE_MINUTES
        var durationGood: Duration
        var durationEasy: Duration

        var ivlHard = 0
        var ivlGood = 0
        var ivlEasy: Int

        val txtHard: String
        val txtGood: String
        val txtEasy: String

        when (flashCard.phase) {
            CardPhase.Added.value -> {
                stateAgain = initState(Rating.Again)
                stateHard = initState(Rating.Hard)
                stateGood = initState(Rating.Good)
                stateEasy = initState(Rating.Easy)

                ivlEasy = 1

                txtHard = "5m"
                txtGood = "10m"
                txtEasy = "1d"

                durationHard = FIVE_MINUTES
                durationGood = TEN_MINUTES
                durationEasy = ONE_DAY
            }

            CardPhase.ReLearning.value -> {
                if (!hasUsableState(flashCard)) {
                    stateAgain = initState(Rating.Again)
                    stateHard = initState(Rating.Hard)
                    stateGood = initState(Rating.Good)
                    stateEasy = initState(Rating.Easy)
                } else {
                    val lastD = clampDifficulty(flashCard.difficulty)
                    val lastS = clampStability(flashCard.stability)
                    val elapsedDays = flashCard.elapsedDays.coerceAtLeast(0.0)

                    stateAgain = nextState(lastD, lastS, elapsedDays, Rating.Again)
                    stateHard = nextState(lastD, lastS, elapsedDays, Rating.Hard)
                    stateGood = nextState(lastD, lastS, elapsedDays, Rating.Good)
                    stateEasy = nextState(lastD, lastS, elapsedDays, Rating.Easy)
                }

                ivlGood = nextInterval(stateGood.stability, Rating.Good, fuzzSeed = fuzzSeed)
                ivlEasy = nextInterval(stateEasy.stability, Rating.Easy, fuzzSeed = fuzzSeed)

                val ordered = enforceGoodEasyOrder(ivlGood, ivlEasy)
                ivlGood = ordered.first
                ivlEasy = ordered.second

                txtHard = "10m"
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)

                durationHard = TEN_MINUTES
                durationGood = ivlGood.days
                durationEasy = ivlEasy.days
            }

            else -> {
                val interval = flashCard.interval.coerceAtLeast(1)
                val elapsedDays = flashCard.elapsedDays.coerceAtLeast(0.0)
                val lastD = clampDifficulty(flashCard.difficulty)
                val lastS = clampStability(flashCard.stability)

                stateAgain = nextState(lastD, lastS, elapsedDays, Rating.Again)
                stateHard = nextState(lastD, lastS, elapsedDays, Rating.Hard)
                stateGood = nextState(lastD, lastS, elapsedDays, Rating.Good)
                stateEasy = nextState(lastD, lastS, elapsedDays, Rating.Easy)

                ivlHard = nextInterval(stateHard.stability, Rating.Hard, lastInterval = interval, fuzzSeed = fuzzSeed)
                ivlGood = nextInterval(stateGood.stability, Rating.Good, lastInterval = interval, fuzzSeed = fuzzSeed)
                ivlEasy = nextInterval(stateEasy.stability, Rating.Easy, lastInterval = interval, fuzzSeed = fuzzSeed)

                val ordered = enforceHardGoodEasyOrder(ivlHard, ivlGood, ivlEasy)
                ivlHard = ordered.first
                ivlGood = ordered.second
                ivlEasy = ordered.third

                txtHard = convertDays(ivlHard)
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)

                durationHard = ivlHard.days
                durationGood = ivlGood.days
                durationEasy = ivlEasy.days
            }
        }

        return listOf(
            Grade(
                "Again",
                AGAIN_DURATION.inWholeMilliseconds,
                0,
                "<3m",
                Rating.Again,
                stateAgain.stability,
                stateAgain.difficulty,
            ),
            Grade(
                "Hard",
                durationHard.inWholeMilliseconds,
                ivlHard,
                txtHard,
                Rating.Hard,
                stateHard.stability,
                stateHard.difficulty,
            ),
            Grade(
                "Good",
                durationGood.inWholeMilliseconds,
                ivlGood,
                txtGood,
                Rating.Good,
                stateGood.stability,
                stateGood.difficulty,
            ),
            Grade(
                "Easy",
                durationEasy.inWholeMilliseconds,
                ivlEasy,
                txtEasy,
                Rating.Easy,
                stateEasy.stability,
                stateEasy.difficulty,
            ),
        )
    }

    private fun nextState(
        lastD: Double,
        lastS: Double,
        elapsedDays: Double,
        rating: Rating,
    ): InitState {
        val nextD = nextDifficulty(lastD, rating)

        val nextS =
            if (elapsedDays < 1.0) {
                nextShortTermStability(lastS, rating)
            } else {
                val r = forgettingCurve(elapsedDays, lastS)

                if (rating == Rating.Again) {
                    nextForgetStability(lastD, lastS, r)
                } else {
                    nextRecallStability(lastD, lastS, r, rating)
                }
            }

        return InitState(
            difficulty = nextD,
            stability = nextS,
        )
    }

    private fun convertDays(days: Int): String =
        when {
            days >= 365 -> "${days / 365}y"
            days >= 30 -> "${days / 30}mo"
            else -> "${days}d"
        }

    private fun applyFuzz(
        interval: Double,
        rating: Rating,
        scheduledDays: Int = 0,
        fuzzSeed: Long? = null,
    ): Double {
        if (!enableFuzz || interval < 2.5) return interval

        val ivl = interval.roundToInt()
        var minIvl = max(2, (ivl * 0.95 - 1).roundToInt())
        var maxIvl = (ivl * 1.05 + 1).roundToInt().coerceAtMost(maximumInterval)

        if (isReview && ivl > scheduledDays) {
            minIvl = max(minIvl, scheduledDays + 1)
        }

        minIvl = min(minIvl, maxIvl)

        val fuzzFactor = fuzzFactorProvider(
            FuzzContext(
                seed = fuzzSeed,
                rating = rating,
                interval = ivl,
                scheduledDays = scheduledDays,
            )
        ).coerceIn(0.0, 1.0)
        return floor(fuzzFactor * (maxIvl - minIvl + 1) + minIvl)
            .coerceIn(minIvl.toDouble(), maxIvl.toDouble())
    }

    private fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        val s = clampStability(stability)
        val elapsed = elapsedDays.coerceAtLeast(0.0)

        return (1.0 + factor * elapsed / s)
            .pow(decay)
            .coerceIn(0.0, 1.0)
    }

    private fun initDifficultyRaw(rating: Rating): Double {
        val base = params[4]
        val exponent = params[5] * (rating.value - 1)
        return base - exp(exponent) + 1.0
    }

    private fun initDifficulty(rating: Rating): Double =
        clampDifficulty(initDifficultyRaw(rating))

    private fun initStability(rating: Rating): Double {
        val index = rating.value - 1
        return clampStability(params[index])
    }

    private fun initState(rating: Rating): InitState =
        InitState(
            difficulty = initDifficulty(rating),
            stability = initStability(rating),
        )

    private fun linearDamping(delta: Double, oldD: Double): Double =
        delta * (10.0 - oldD) / 9.0

    private fun meanReversion(initD: Double, nextD: Double): Double =
        params[7] * initD + (1.0 - params[7]) * nextD

    private fun nextInterval(
        stability: Double,
        rating: Rating,
        maxInterval: Int = maximumInterval,
        lastInterval: Int = 0,
        fuzzSeed: Long? = null,
    ): Int {
        val s = clampStability(stability)
        val rawInterval = s / factor * (requestRetention.pow(1.0 / decay) - 1.0)
        val fuzzed = applyFuzz(rawInterval, rating = rating, scheduledDays = lastInterval, fuzzSeed = fuzzSeed)

        return fuzzed.roundToInt().coerceIn(1, maxInterval)
    }

    private fun nextDifficulty(currentD: Double, rating: Rating): Double {
        val d = clampDifficulty(currentD)
        val deltaD = -params[6] * (rating.value - 3)
        val damped = linearDamping(deltaD, d)
        val nextD = d + damped

        val reverted = meanReversion(initDifficultyRaw(Rating.Easy), nextD)

        return clampDifficulty(reverted)
    }

    private fun nextShortTermStability(currentS: Double, rating: Rating): Double {
        val s = clampStability(currentS)

        var sinc = exp(params[17] * (rating.value - 3 + params[18])) *
                s.pow(-params[19])

        if (rating.value >= 3) {
            sinc = max(sinc, 1.0)
        }

        return clampStability(s * sinc)
    }

    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double {
        val d = clampDifficulty(difficulty)
        val s = clampStability(stability)
        val r = retrievability.coerceIn(0.0, 1.0)

        val sMin = s / exp(params[17] * params[18])

        val result = params[11] *
                d.pow(-params[12]) *
                ((s + 1.0).pow(params[13]) - 1.0) *
                exp((1.0 - r) * params[14])

        return clampStability(min(result, sMin))
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val d = clampDifficulty(difficulty)
        val s = clampStability(stability)
        val r = retrievability.coerceIn(0.0, 1.0)

        val hardPenalty = if (rating == Rating.Hard) params[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) params[16] else 1.0

        val stabilityIncrease = exp(params[8]) *
                (11.0 - d) *
                s.pow(-params[9]) *
                (exp((1.0 - r) * params[10]) - 1.0) *
                hardPenalty *
                easyBonus

        return clampStability(s * (1.0 + stabilityIncrease))
    }

    private fun enforceGoodEasyOrder(
        goodInterval: Int,
        easyInterval: Int,
    ): Pair<Int, Int> {
        if (maximumInterval <= 1) {
            val only = goodInterval.coerceIn(1, maximumInterval)
            return only to only
        }

        var good = goodInterval.coerceIn(1, maximumInterval)
        var easy = easyInterval.coerceIn(1, maximumInterval)

        easy = max(easy, good + 1)

        if (easy > maximumInterval) {
            easy = maximumInterval
            good = min(good, easy - 1).coerceAtLeast(1)
        }

        return good to easy
    }

    private fun enforceHardGoodEasyOrder(
        hardInterval: Int,
        goodInterval: Int,
        easyInterval: Int,
    ): Triple<Int, Int, Int> {
        if (maximumInterval < 3) {
            return Triple(
                hardInterval.coerceIn(1, maximumInterval),
                goodInterval.coerceIn(1, maximumInterval),
                easyInterval.coerceIn(1, maximumInterval),
            )
        }

        var hard = hardInterval.coerceIn(1, maximumInterval)
        var good = goodInterval.coerceIn(1, maximumInterval)
        var easy = easyInterval.coerceIn(1, maximumInterval)

        hard = min(hard, good)
        good = max(good, hard + 1)
        easy = max(easy, good + 1)

        if (easy > maximumInterval) {
            easy = maximumInterval
            good = min(good, easy - 1).coerceAtLeast(1)
            hard = min(hard, good).coerceAtLeast(1)
        }

        return Triple(hard, good, easy)
    }

    private fun hasUsableState(flashCard: FlashCard): Boolean =
        flashCard.difficulty.isFiniteValue() &&
                flashCard.stability.isFiniteValue() &&
                flashCard.difficulty in DIFFICULTY_MIN..DIFFICULTY_MAX &&
                flashCard.stability >= STABILITY_MIN

    private fun clampDifficulty(value: Double): Double =
        if (value.isFiniteValue()) {
            value.coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX)
        } else {
            DIFFICULTY_MIN
        }

    private fun clampStability(value: Double): Double =
        if (value.isFiniteValue()) {
            value.coerceAtLeast(STABILITY_MIN)
        } else {
            STABILITY_MIN
        }

    private fun Double.isFiniteValue(): Boolean =
        !isNaN() && !isInfinite()
}
