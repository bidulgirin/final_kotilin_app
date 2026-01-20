package com.final_pj.voice.feature.mypage

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.final_pj.voice.R
import com.final_pj.voice.databinding.FragmentMyPageBinding
import com.final_pj.voice.feature.login.TokenStore
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MyPageFragment : Fragment() {

    private var _binding: FragmentMyPageBinding? = null
    private val binding get() = _binding!!

    private enum class Period { DAILY, MONTHLY }
    private var currentPeriod: Period = Period.DAILY

    private val dailyCallsProducer = CartesianChartModelProducer()
    private val dailySuspiciousProducer = CartesianChartModelProducer()

    private val callsLabelsKey = ExtraStore.Key<List<String>>()
    private val suspiciousLabelsKey = ExtraStore.Key<List<String>>()

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

        val name = tokenStore.getUserName() ?: "사용자"
        val email = tokenStore.getUserEmail() ?: ""

        binding.tvUserName.text = name
        binding.tvUserEmail.text = email

        binding.btnBackSetting.setOnClickListener { findNavController().popBackStack() }

        // Vico 차트 초기화 (마커 추가)
        setupVicoChart(binding.chartDailyCalls, dailyCallsProducer, callsLabelsKey, R.color.primary_blue)
        setupVicoChart(binding.chartDailySuspiciousCalls, dailySuspiciousProducer, suspiciousLabelsKey, R.color.red)

        setupDonutChart(binding.chartTopCategories)

        binding.togglePeriod.check(binding.btnDaily.id)
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentPeriod = if (checkedId == binding.btnMonthly.id) Period.MONTHLY else Period.DAILY
            updateCharts(currentPeriod)
        }

        updateCharts(currentPeriod)
    }

    private fun createMarker(): CartesianMarker {
        val labelBackground = ShapeComponent(
            fill = Fill(ContextCompat.getColor(requireContext(), R.color.component_bg)),
            shape = CorneredShape.Pill,
            strokeFill = Fill(ContextCompat.getColor(requireContext(), R.color.primary_blue)),
            strokeThicknessDp = 1f
        )
        val label = TextComponent(
            color = ContextCompat.getColor(requireContext(), R.color.main_text),
            background = labelBackground,
            padding = Insets(8f, 4f, 8f, 4f),
            typeface = Typeface.DEFAULT_BOLD,
            textSizeSp = 12f
        )
        return DefaultCartesianMarker(label = label)
    }

    private fun setupVicoChart(
        chartView: com.patrykandpatrick.vico.views.cartesian.CartesianChartView,
        producer: CartesianChartModelProducer,
        labelsKey: ExtraStore.Key<List<String>>,
        colorResId: Int
    ) {
        chartView.modelProducer = producer
        
        val mainColor = ContextCompat.getColor(requireContext(), colorResId)
        val labelColor = ContextCompat.getColor(requireContext(), R.color.sub_text)

        val columnLayer = ColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                LineComponent(
                    fill = Fill(mainColor),
                    thicknessDp = 12f,
                    shape = CorneredShape.Pill
                )
            )
        )

        // 라벨 포매터: ExtraStore에서 라벨 목록을 안전하게 조회
        val labelFormatter = CartesianValueFormatter { context, value, _ ->
            val labels = context.model.extraStore.getOrNull(labelsKey)
            labels?.getOrNull(value.toInt()) ?: ""
        }

        chartView.chart = CartesianChart(
            columnLayer,
            bottomAxis = HorizontalAxis.bottom(
                label = TextComponent(
                    color = labelColor,
                    textSizeSp = 10f,
                    margins = Insets(topDp = 4f)
                ),
                valueFormatter = labelFormatter,
                tick = null,
                guideline = null
            ),
            startAxis = VerticalAxis.start(
                label = TextComponent(
                    color = labelColor,
                    textSizeSp = 10f
                ),
                tick = null,
                itemPlacer = VerticalAxis.ItemPlacer.step(step = { 1.0 }),
                title = "건수",
                titleComponent = TextComponent(
                    color = labelColor,
                    textSizeSp = 10f
                )
            ),
            marker = createMarker()
        )
    }

    private fun setupDonutChart(pieChart: PieChart) {
        pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            dragDecelerationFrictionCoef = 0.95f
            
            isDrawHoleEnabled = true
            setHoleColor(android.graphics.Color.TRANSPARENT)
            setTransparentCircleColor(android.graphics.Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 65f
            transparentCircleRadius = 70f
            
            setDrawCenterText(true)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            
            animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutQuad)
            
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                xEntrySpace = 10f
                yEntrySpace = 5f
                yOffset = 5f
                textColor = ContextCompat.getColor(requireContext(), R.color.main_text)
                textSize = 11f
            }
            
            setDrawEntryLabels(false)
        }
    }

    private fun updateCharts(period: Period) {
        when (period) {
            Period.DAILY -> {
                binding.tvCallsTitle.text = "오늘 시간대별 통화량"
                binding.tvSuspiciousTitle.text = "오늘 시간대별 의심 통화량"

                val (labels, calls, suspicious) = makeHourlySample()
                val top5 = makeTop5DailySample()

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

                renderTop5Donut(top5)
                updateSummary(top5)
            }

            Period.MONTHLY -> {
                binding.tvCallsTitle.text = "최근 7일간 통화량"
                binding.tvSuspiciousTitle.text = "최근 7일간 의심 통화량"

                val (labels, calls, suspicious) = makeDailySample()
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

                renderTop5Donut(top5)
                updateSummary(top5)
            }
        }
    }

    private fun updateSummary(top5: List<Pair<String, Int>>) {
        binding.tvCategorySummary.text = top5
            .mapIndexed { i, (label, count) -> "${i + 1}) $label: ${count}회" }
            .joinToString("\n")
    }

    private fun renderTop5Donut(top5: List<Pair<String, Int>>) {
        val pieChart: PieChart = binding.chartTopCategories
        val chartColors = listOf(
            ContextCompat.getColor(requireContext(), R.color.chart_1),
            ContextCompat.getColor(requireContext(), R.color.chart_2),
            ContextCompat.getColor(requireContext(), R.color.chart_3),
            ContextCompat.getColor(requireContext(), R.color.chart_4),
            ContextCompat.getColor(requireContext(), R.color.chart_5)
        )

        val entries = top5.map { (label, count) -> PieEntry(count.toFloat(), label) }

        val dataSet = PieDataSet(entries, "").apply {
            sliceSpace = 4f
            selectionShift = 8f
            colors = chartColors
            setDrawValues(true)
            valueTextSize = 11f
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.pure_white)
            valueTypeface = Typeface.DEFAULT_BOLD
            valueFormatter = PercentFormatter(pieChart)
        }

        pieChart.data = PieData(dataSet)
        
        val total = top5.sumOf { it.second }
        val centerTextColor = ContextCompat.getColor(requireContext(), R.color.main_text)
        
        pieChart.centerText = android.text.SpannableString("분석 결과\n총 ${total}건").apply {
            setSpan(android.text.style.RelativeSizeSpan(0.8f), 0, 5, 0)
            setSpan(android.text.style.StyleSpan(Typeface.BOLD), 6, length, 0)
            setSpan(android.text.style.ForegroundColorSpan(centerTextColor), 0, length, 0)
        }
        
        pieChart.invalidate()
    }

    private fun makeHourlySample(): Triple<List<String>, List<Int>, List<Int>> {
        val labels = listOf("00시", "03시", "06시", "09시", "12시", "15시", "18시", "21시")
        val calls = listOf(1, 0, 0, 4, 8, 5, 12, 3)
        val suspicious = listOf(0, 0, 0, 1, 2, 0, 4, 1)
        return Triple(labels, calls, suspicious)
    }

    private fun makeDailySample(): Triple<List<String>, List<Int>, List<Int>> {
        val today = LocalDate.now()
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val labelFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.KOREA)
        val labels = dates.map { it.format(labelFmt) }
        return Triple(labels, listOf(15, 22, 18, 25, 14, 10, 20), listOf(2, 4, 1, 5, 2, 0, 3))
    }

    private fun makeTop5DailySample(): List<Pair<String, Int>> =
        listOf("스팸 의심" to 12, "보이스피싱" to 9, "광고" to 7, "설문" to 4, "기타" to 2)

    private fun makeTop5MonthlySample(): List<Pair<String, Int>> =
        listOf("보이스피싱" to 33, "스팸 의심" to 28, "광고" to 18, "설문" to 10, "기타" to 6)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
