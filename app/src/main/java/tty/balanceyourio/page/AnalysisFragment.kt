package tty.balanceyourio.page

import android.annotation.TargetApi
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.android.synthetic.main.fragment_analysis.*
import tty.balanceyourio.R
import tty.balanceyourio.data.BYIOHelper
import tty.balanceyourio.data.BillRecordsProvider
import tty.balanceyourio.model.BillRecord
import tty.balanceyourio.model.IOType
import tty.balanceyourio.model.TimeMode
import tty.balanceyourio.util.DateConverter
import tty.balanceyourio.util.NumberFormatter
import tty.balanceyourio.util.NumberFormatter.decimalFormat0
import tty.balanceyourio.util.NumberFormatter.decimalFormat2
import java.util.*
import kotlin.collections.ArrayList

@TargetApi(Build.VERSION_CODES.M)
class AnalysisFragment : Fragment(), RadioGroup.OnCheckedChangeListener, OnChartValueSelectedListener {
    override fun onNothingSelected() {
        Log.d(TAG, "Nothing Selected")
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {
        Log.d(TAG, "Entry Selected X: ${decimalFormat0.format(e?.x)} Y: ${decimalFormat2.format(e?.y?.toDouble()?.let { NumberFormatter.logToDouble(it)-1 })}")
        //TODO SELECTED UI
        val pos = decimalFormat0.format(e?.x).toInt()
        if(pos !in 0 until timeModeBillRecord.size){
            return
        }
        setDataForDetailTypeChart(detailTypeChart, timeModeBillRecord[pos])
    }

    override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
        when(checkedId) {
            R.id.radio_mode_day -> {
                timeMode=TimeMode.Day
            }

            R.id.radio_mode_week -> {
                timeMode=TimeMode.Week
            }

            R.id.radio_mode_month -> {
                timeMode=TimeMode.Month
            }

            R.id.radio_mode_year -> {
                timeMode=TimeMode.Year
            }
        }

        updateData()
    }

    fun updateData() {
        getDataAndShow()
    }

    private var helper: BYIOHelper? = null
    private lateinit var data:ArrayList<BillRecord>
    private lateinit var timeModeIOList: ArrayList<HashMap<IOType, Double>>
    private lateinit var timeModeList: ArrayList<Date>
    private lateinit var timeModeBillRecord: ArrayList<ArrayList<BillRecord>>
    private var timeMode=TimeMode.Day
    private lateinit var timeModeChart: LineChart
    private lateinit var detailTypeChart: BarChart
    private lateinit var tfBold: Typeface
    private var timeModeChartZoomRatio = 0F

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        helper = context?.let { BYIOHelper(it) }
        return inflater.inflate(R.layout.fragment_analysis, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timeModeChart=time_line_chart
        detailTypeChart=detail_bar_chart

        tfBold = Typeface.createFromAsset(context!!.assets, "OpenSans-Bold.ttf")

        //region 设置图表属性
        timeModeChart.description.isEnabled=false
        timeModeChart.setNoDataText("暂无数据")
        timeModeChart.setTouchEnabled(true)
        timeModeChart.isDoubleTapToZoomEnabled=false
        timeModeChart.isDragEnabled = true
        timeModeChart.isScaleXEnabled = false
        timeModeChart.isScaleYEnabled = false
        timeModeChart.setPinchZoom(false)
        timeModeChart.setOnChartValueSelectedListener(this)
        timeModeChart.isDragDecelerationEnabled=true
        timeModeChart.dragDecelerationFrictionCoef=0.5F

        val xAxisTimeMode = timeModeChart.xAxis
        xAxisTimeMode.setLabelCount(7, false)
        xAxisTimeMode.setAvoidFirstLastClipping(true)
        xAxisTimeMode.textColor = resources.getColor(R.color.colorNormal, null)
        xAxisTimeMode.position = XAxis.XAxisPosition.BOTTOM
        xAxisTimeMode.setDrawGridLines(false)
        xAxisTimeMode.isGranularityEnabled = true
        xAxisTimeMode.axisLineColor = resources.getColor(R.color.colorNormal, null)


        timeModeChart.axisLeft.isEnabled=true
        timeModeChart.axisRight.isEnabled=true
        timeModeChart.axisLeft.axisMaximum=13.9F
        timeModeChart.axisLeft.axisMinimum=-13.9F
        timeModeChart.axisRight.axisMinimum=-13.9F
        timeModeChart.axisRight.axisMaximum=13.9F
        timeModeChart.axisRight.setDrawZeroLine(true)
        timeModeChart.axisLeft.setDrawZeroLine(true)
        timeModeChart.axisLeft.valueFormatter=object : ValueFormatter(){
            override fun getFormattedValue(value: Float): String {
                return ""
            }
        }

        timeModeChart.axisRight.valueFormatter=object : ValueFormatter(){
            override fun getFormattedValue(value: Float): String {
                return ""
            }
        }

        timeModeChart.axisLeft.zeroLineColor=resources.getColor(R.color.colorNormalDark, null)
        timeModeChart.axisRight.zeroLineColor=resources.getColor(R.color.colorNormalDark, null)

        xAxisTimeMode.valueFormatter=object : ValueFormatter(){
            override fun getFormattedValue(value: Float): String {
//                Log.d(TAG, decimalFormat0.format(value))
                val pos=decimalFormat0.format(value).toInt()
                if(pos<0 || pos>=timeModeList.size){
                    return "..."
                }
                return DateConverter.getXAxisDate(timeModeList[pos], timeMode)
            }
        }

        //endregion
        //region 设置细节图表属性

        detailTypeChart.description.isEnabled=false
        detailTypeChart.setNoDataText("该时间段暂无数据")
        detailTypeChart.setTouchEnabled(true)
        detailTypeChart.isDoubleTapToZoomEnabled=false
        detailTypeChart.isDragEnabled = true
//        detailTypeChart.isScaleXEnabled = false
//        detailTypeChart.isScaleYEnabled = false
        detailTypeChart.setPinchZoom(false)
        detailTypeChart.isDragDecelerationEnabled=true
        detailTypeChart.dragDecelerationFrictionCoef=0.5F
        detailTypeChart.setFitBars(false)

        //endregion



        choose_chart_view_mode.setOnCheckedChangeListener(this)

    }


    override fun onResume() {
        super.onResume()
        getDataAndShow()
    }

    private fun getDataAndShow() {
        data = helper!!.getBill()

        val join= BillRecordsProvider.getBillRecordsByTimeMode(data, timeMode)
        timeModeBillRecord = join.item1
        timeModeIOList = join.item2
        timeModeList = join.item3

        timeModeChart.zoom(0F, 1F, 0F, 0F)
        timeModeChartZoomRatio = timeModeIOList.size.toFloat() / when(timeMode){
            TimeMode.Day -> 6
            TimeMode.Week -> 5
            TimeMode.Month -> 4
            TimeMode.Year -> 5
        }
        timeModeChart.zoom(timeModeChartZoomRatio, 1F, 0F, 0F)
        setDataForTimeModeChart(timeModeChart, timeModeIOList)

        timeModeChart.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AF destroy")
        helper?.close()
    }

    private fun setDataForTimeModeChart(chart: LineChart, statistics: ArrayList<HashMap<IOType, Double>>) {

        val valuesOutcome = ArrayList<Entry>()
        val valuesIncome = ArrayList<Entry>()

        for (i in 0 until statistics.size) {
            val valueOutcome: Float = Math.log(statistics[i][IOType.Outcome]!!+1).toFloat()
            val valueIncome: Float = Math.log(statistics[i][IOType.Income]!!+1).toFloat()
            valuesOutcome.add(Entry(i.toFloat(), -valueOutcome))
            valuesIncome.add(Entry(i.toFloat(), valueIncome))
        }

        val outcomeSet: LineDataSet
        val incomeSet: LineDataSet
        if (chart.data != null && chart.data.dataSetCount > 0) {
            outcomeSet = chart.data.getDataSetByIndex(0) as LineDataSet
            outcomeSet.values = valuesOutcome
            incomeSet = chart.data.getDataSetByIndex(1) as LineDataSet
            incomeSet.values = valuesIncome
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
        } else {
            outcomeSet = LineDataSet(valuesOutcome, "支出")
            outcomeSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            outcomeSet.cubicIntensity = 0.2f
            outcomeSet.lineWidth = 1.8f
            outcomeSet.circleRadius = 2.4f
            outcomeSet.setCircleColor(resources.getColor(R.color.typeOutcome, null))
            outcomeSet.highLightColor = resources.getColor(R.color.typeOutcome, null)
            outcomeSet.color = resources.getColor(R.color.typeOutcome, null)
//            outcomeSet.fillColor = resources.getColor(R.color.typeOutcome, null)
//            outcomeSet.fillAlpha = 20
//            outcomeSet.setDrawHorizontalHighlightIndicator(true)
//            outcomeSet.fillFormatter = IFillFormatter { _, _ -> chart.axisLeft.axisMaximum }
//            outcomeSet.fillFormatter = IFillFormatter { _, _ -> 0F }
            outcomeSet.setDrawFilled(true)
            outcomeSet.fillDrawable=ColorDrawable(resources.getColor(R.color.typeOutcomeAlpha, null))
            outcomeSet.fillAlpha = 20
            outcomeSet.setDrawValues(true)
            outcomeSet.axisDependency=YAxis.AxisDependency.LEFT
            outcomeSet.valueTextColor = resources.getColor(R.color.typeOutcome, null)
            outcomeSet.valueFormatter=object : ValueFormatter(){
                override fun getFormattedValue(value: Float): String {
                    return decimalFormat2.format(Math.pow(Math.E, -value.toDouble())-1)
                }
            }

            incomeSet = LineDataSet(valuesIncome, "收入")
            incomeSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            incomeSet.cubicIntensity = 0.2f
            incomeSet.lineWidth = 1.8f
            incomeSet.circleRadius = 2.4f
            incomeSet.setCircleColor(resources.getColor(R.color.typeIncome, null))
            incomeSet.highLightColor = resources.getColor(R.color.typeIncome, null)
            incomeSet.color = resources.getColor(R.color.typeIncome, null)
//            incomeSet.fillColor = resources.getColor(R.color.typeIncome, null)
//            incomeSet.fillAlpha = 100
//            incomeSet.setDrawHorizontalHighlightIndicator(true)
//            incomeSet.fillFormatter = IFillFormatter { _, _ -> chart.axisRight.axisMinimum }
//            incomeSet.fillFormatter = IFillFormatter { _, _ -> 0F }
            incomeSet.setDrawFilled(true)
            incomeSet.fillDrawable=ColorDrawable(resources.getColor(R.color.typeIncomeAlpha, null))
            incomeSet.setDrawValues(true)
            incomeSet.axisDependency=YAxis.AxisDependency.RIGHT
            incomeSet.valueTextColor = resources.getColor(R.color.typeIncome, null)
            incomeSet.valueFormatter=object : ValueFormatter(){
                override fun getFormattedValue(value: Float): String {
                    return decimalFormat2.format(Math.pow(Math.E, value.toDouble())-1)
                }
            }

            val data = LineData(outcomeSet, incomeSet)
            data.setValueTypeface(tfBold)
            data.setValueTextSize(9f)

            chart.data = data
        }
    }

    private fun setDataForDetailTypeChart(chart: BarChart, detail: ArrayList<BillRecord>){
        val barWidth = 5f
        val spaceForBar = 8f
        val values = ArrayList<BarEntry>()

        for (i in 0 until detail.size) {
            val `val` = detail[i].amount.toFloat()
            values.add(
                BarEntry(i * spaceForBar, `val`)
            )
        }

        val set1: BarDataSet

        if (chart.data != null && chart.data.dataSetCount > 0) {
            set1 = chart.data.getDataSetByIndex(0) as BarDataSet
            set1.values = values
            chart.data.notifyDataChanged()
            chart.notifyDataSetChanged()
        } else {
            set1 = BarDataSet(values, "DataSet 1")

            set1.setDrawIcons(false)

            val dataSets = ArrayList<IBarDataSet>()
            dataSets.add(set1)

            val data = BarData(dataSets)
            data.setValueTextSize(10f)
            data.setValueTypeface(tfBold)
            data.barWidth = barWidth
            chart.data = data

        }

        chart.invalidate()
    }

    companion object{
        const val TAG = "AF"
    }
}
