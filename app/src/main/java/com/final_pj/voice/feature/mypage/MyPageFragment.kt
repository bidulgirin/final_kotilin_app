package com.final_pj.voice.feature.mypage

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.databinding.FragmentMyPageBinding
import com.final_pj.voice.feature.login.TokenStore
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MyPageFragment : Fragment() {

    private var _binding: FragmentMyPageBinding? = null
    private val binding get() = _binding!!

    private enum class Period { DAILY, MONTHLY }
    private var currentPeriod: Period = Period.DAILY

    // ✅ Vico(막대)용 producer 2개만 사용
    private val dailyCallsProducer = CartesianChartModelProducer()
    private val dailySuspiciousProducer = CartesianChartModelProducer()

    // 라벨 저장용 key (Vico 2개만)
    private val callsLabelsKey = ExtraStore.Key<List<String>>()
    private val suspiciousLabelsKey = ExtraStore.Key<List<String>>()

    // TokenStore 인스턴스
    private val tokenStore by lazy { TokenStore(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 사용자 정보 로드 (TokenStore 사용)
        val name = tokenStore.getUserName() ?: "이름 없음"
        val email = tokenStore.getUserEmail() ?: "이메일 정보 없음"

        binding.tvUserName.text = "이름: $name"
        binding.tvUserEmail.text = "이메일: $email"

        binding.btnBackSetting.setOnClickListener { findNavController().popBackStack() }
        //binding.btnEditProfile.setOnClickListener { /* TODO */ }

        // Vico 차트 producer 연결 (2개만)
        binding.chartDailyCalls.modelProducer = dailyCallsProducer
        binding.chartDailySuspiciousCalls.modelProducer = dailySuspiciousProducer

        // Vico 차트 2개만 라벨 포매터 적용
        attachBottomAxisLabelFormatter(binding.chartDailyCalls, callsLabelsKey)
        attachBottomAxisLabelFormatter(binding.chartDailySuspiciousCalls, suspiciousLabelsKey)

        // PieChart(도넛) 기본 설정 (한번만)
        setupDonutChart(binding.chartTopCategories)

        // 토글 초기값: 일별
        binding.togglePeriod.check(binding.btnDaily.id)
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentPeriod = if (checkedId == binding.btnMonthly.id) Period.MONTHLY else Period.DAILY
            updateCharts(currentPeriod)
        }

        // 최초 렌더
        updateCharts(currentPeriod)
    }

    /**
     * Vico 차트 X축 라벨: 인덱스(0..n-1) -> 문자열 라벨
     */
    private fun attachBottomAxisLabelFormatter(
        chartView: com.patrykandpatrick.vico.views.cartesian.CartesianChartView,
        labelsKey: ExtraStore.Key<List<String>>
    ) {
        val formatter = CartesianValueFormatter { context, value, _ ->
            // extras 아직 없을 때도 안전하게
            val labels = context.model.extraStore.getOrNull(labelsKey).orEmpty()
            labels.getOrNull(value.toInt()) ?: ""
        }

        val newBottom: Axis<Axis.Position.Horizontal.Bottom> =
            HorizontalAxis.bottom(valueFormatter = formatter)

        val existing = chartView.chart
        chartView.chart = if (existing != null) {
            existing.copy(bottomAxis = newBottom)
        } else {
            com.patrykandpatrick.vico.core.cartesian.CartesianChart(bottomAxis = newBottom)
        }
    }

    private fun updateCharts(period: Period) {
        when (period) {
            Period.DAILY -> {
                binding.tvCallsTitle.text = "일별 통화 수"
                binding.tvSuspiciousTitle.text = "일별 의심 통화 수"

                val (labels, calls, suspicious) = makeDailySample()
                val top5 = makeTop5DailySample()

                // ✅ Vico 막대 차트 2개 업데이트 + 라벨 extras 주입
                lifecycleScope.launch {
                    dailyCallsProducer.runTransaction {
                        columnSeries { series(calls) }
                        extras { it[callsLabelsKey] = labels }
                    }
                    dailySuspiciousProducer.runTransaction {
                        columnSeries { series(suspicious) }
                        extras { it[suspiciousLabelsKey] = labels }
                    }
                }

                // ✅ 도넛 업데이트(핵심)
                renderTop5Donut(top5)

                binding.tvCategorySummary.text = top5
                    .mapIndexed { i, (label, count) -> "${i + 1}) $label: ${count}회" }
                    .joinToString("\n")
            }

            Period.MONTHLY -> {
                binding.tvCallsTitle.text = "월별 통화 수"
                binding.tvSuspiciousTitle.text = "월별 의심 통화 수"

                val (labels, calls, suspicious) = makeMonthlySample()
                val top5 = makeTop5MonthlySample()

                lifecycleScope.launch {
                    dailyCallsProducer.runTransaction {
                        columnSeries { series(calls) }
                        extras { it[callsLabelsKey] = labels }
                    }
                    dailySuspiciousProducer.runTransaction {
                        columnSeries { series(suspicious) }
                        extras { it[suspiciousLabelsKey] = labels }
                    }
                }

                // ✅ 도넛 업데이트(핵심)
                renderTop5Donut(top5)

                binding.tvCategorySummary.text = top5
                    .mapIndexed { i, (label, count) -> "${i + 1}) $label: ${count}회" }
                    .joinToString("\n")
            }
        }
    }

    // -----------------------
    // 샘플 데이터 (나중에 Room/서버로 교체)
    // -----------------------

    private fun makeDailySample(): Triple<List<String>, List<Int>, List<Int>> {
        val today = LocalDate.now()
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val labelFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.KOREA)
        val labels = dates.map { it.format(labelFmt) }

        val calls = listOf(3, 8, 6, 10, 4, 2, 9)
        val suspicious = listOf(0, 1, 0, 2, 1, 0, 3)
        return Triple(labels, calls, suspicious)
    }

    private fun makeMonthlySample(): Triple<List<String>, List<Int>, List<Int>> {
        val thisMonth = YearMonth.now()
        val months = (5 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
        val labels = months.map { "${it.monthValue}월" }

        val calls = listOf(42, 35, 51, 48, 60, 39)
        val suspicious = listOf(6, 4, 7, 5, 9, 3)
        return Triple(labels, calls, suspicious)
    }

    private fun makeTop5DailySample(): List<Pair<String, Int>> =
        listOf("스팸 의심" to 12, "보이스피싱" to 9, "광고" to 7, "설문" to 4, "기타" to 2)

    private fun makeTop5MonthlySample(): List<Pair<String, Int>> =
        listOf("보이스피싱" to 33, "스팸 의심" to 28, "광고" to 18, "설문" to 10, "기타" to 6)

    // -----------------------
    // Donut (MPAndroidChart)
    // -----------------------

    private fun setupDonutChart(pieChart: PieChart) {
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)

        // 도넛 설정
        pieChart.setDrawHoleEnabled(true)
        pieChart.holeRadius = 62f
        pieChart.transparentCircleRadius = 67f

        // 라벨 겹침 방지: 조각 위 라벨은 끄고 범례로 보여주는 편이 깔끔
        pieChart.setDrawEntryLabels(false)

        // 애니메이션(살짝)
        pieChart.animateY(700)

        // 범례는 기본 on
        pieChart.legend.isEnabled = true

        // 중앙 텍스트 스타일은 render 때 값과 함께 세팅
        pieChart.setCenterTextSize(14f)
    }

    private fun renderTop5Donut(top5: List<Pair<String, Int>>) {
        val pieChart: PieChart = binding.chartTopCategories

        val entries = top5.map { (label, count) ->
            PieEntry(count.toFloat(), label)
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.sliceSpace = 2f
        dataSet.valueTextSize = 12f
        dataSet.setDrawValues(true)
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data

        val total = top5.sumOf { it.second }
        pieChart.centerText = "TOP 5\n총 $total"

        pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
