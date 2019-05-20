package tty.balanceyourio.page

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_add_bill.*
import tty.balanceyourio.R
import tty.balanceyourio.adapter.AddBillIconAdapter
import tty.balanceyourio.converter.PxlIconConverter
import tty.balanceyourio.data.BYIOHelper
import tty.balanceyourio.data.BYIOType
import tty.balanceyourio.model.BillRecord
import tty.balanceyourio.model.IOType
import tty.balanceyourio.provider.IOTypeProvider
import tty.balanceyourio.util.DateConverter
import java.text.DecimalFormat
import java.util.*

/**
 * 添加或者修改账目记录的
 * @see AppCompatActivity
 * 主要更改：删除不必要的临时变量，用
 * @see billRecord 代替
 */
class AddBillActivity : AppCompatActivity(),
    RadioGroup.OnCheckedChangeListener,
    TextWatcher, View.OnClickListener,
    AddBillIconAdapter.OnItemClickListener,
    ChooseDateFragment.SendDate {

    //region 变量与临时存储
    private lateinit var recyclerView: RecyclerView

    /**
     * 用于与类型的视图交互的数据，其项有：
     * type::Int 用于表示类型，其中0表示支出，1表示为收入。现在是多余的字段。
     * class::String 名称，有一定的转换规则，当符合"key.?(int)"的模式时，将会转化为资源字典内置的字符串。该字段将匹配
     * @see BillRecord.goodsType
     * icon:Int 图标的索引，需要使用
     * @see PxlIconConverter 来转化为图标的资源值
     * chosen:Boolean 表示该项是否选中
     */
    private lateinit var data: ArrayList<HashMap<String, Any>>
    private lateinit var adapter:AddBillIconAdapter

    /**
     * 用于存储临时的数据
     */
    private var billRecord: BillRecord = BillRecord()

    //private var isDateChoose=false
    private var shouldInputMoneyChange=true
    private val decimalFormat = DecimalFormat("0.00")

    //endregion
    //region 重写方法
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_bill)

        add_bill_radio_group.setOnCheckedChangeListener(this)
        add_bill_bt_save.setOnClickListener(this)
        add_bill_choose_date.setOnClickListener {
            val dialog=ChooseDateFragment()
            dialog.show(this.supportFragmentManager,"CDF")
        }
        add_input_money.addTextChangedListener(this)

        setData(IOType.Outcome)

        recyclerView = add_bill_rec_view
        adapter = AddBillIconAdapter(data, PxlIconConverter())
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = adapter
        val layoutManager=GridLayoutManager(this, 6)
        layoutManager.orientation=GridLayoutManager.VERTICAL
        recyclerView.layoutManager = layoutManager
        adapter.setOnItemClickListener(this)

        //初始化临时数据，防止插入崩溃
        billRecord.init()

        //如果是从更新入口进入则执行恢复数据
        if(intent.extras!=null&&intent.hasExtra("id")){
            billRecord = BYIOHelper(this).getBill(intent.getIntExtra("id",-1))
            Log.d(TAG,"时间=${DateConverter.getSimpleString( billRecord.time!!)}")
        }

        setRecord(billRecord)

        Log.d(TAG, "初始化数据 id=${billRecord.id}")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_bill, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when(item?.itemId){
            R.id.menu_add_bill_settings -> {
                Toast.makeText(this, "编辑分类", Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }


    //endregion

    //region 与界面交互的相关函数
    /**
     * 当账目类型更改的图标点击时发生的事件，其依赖于
     * @see data :用于应用视图更改。被adapter引用；
     * @see adapter :适配器
     */
    override fun onItemClick(v: View?, position: Int) {
        Log.d(TAG, "pos: $position")
        for(p in data){
            p["chosen"]=false
        }
        data[position]["chosen"]=true
        billRecord.goodsType = data[position]["class"] as String

        adapter.notifyDataSetChanged()
    }


    /**
     * 目前该方法仅用于保存事件点击时
     */
    override fun onClick(v: View?) {
        when(v?.id){
            R.id.add_bill_bt_save -> {
                Log.d(TAG, "SAVE")
                try {
                    var flag=false
                    var type=""
                    for(p in data){
                        if(p["chosen"] as Boolean){
                            flag=true
                            type= p["class"] as String
                            break
                        }
                    }
                    if(flag){
                        add_bill_bt_save.isClickable=false
                        Log.i(TAG,
                            "mode: ${
                            when(add_bill_radio_group.checkedRadioButtonId){
                                R.id.add_bill_radio_income -> "income"
                                R.id.add_bill_radio_outcome -> "outcome"
                                R.id.add_bill_radio_others -> "others"
                                else -> "#UNSET"
                            }}, " +
                                    "type: $type, amount: ${billRecord.amount}")

                        val helper = BYIOHelper(this)
                        helper.setBill(billRecord)

                        Log.d(TAG,"添加了一条记录")

                        //endregion
                        //helper.printBill()

//                        Toast.makeText(this,
//                            "mode: ${
//                            when(add_bill_radio_group.checkedRadioButtonId){
//                                R.id.add_bill_radio_income -> "income"
//                                R.id.add_bill_radio_outcome -> "outcome"
//                                R.id.add_bill_radio_others -> "others"
//                                else -> "#UNSET"
//                            }}, " +
//                                    "type: $type, amount: ${billRecord.amount}", Toast.LENGTH_SHORT).show()
                        finish()
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        Toast.makeText(this, "类型不能为空！", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: TypeCastException){

                }

            }
        }
    }

    override fun afterTextChanged(s: Editable?) {}

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if(shouldInputMoneyChange){
            try {
                if(s?.length!! >0&&s.toString().toDouble()>=0){
                    billRecord.amount=s.toString().toDouble()
                    billRecord.amount=decimalFormat.format(billRecord.amount).toDouble()
                    add_show_now_money.text= "${resources.getString(R.string.currency)} ${billRecord.amount}"
                } else if(s.isEmpty()) {
                    billRecord.amount=0.0
                    add_show_now_money.text= "${resources.getString(R.string.currency)} ${billRecord.amount}"
                }
                if(billRecord.amount>999999){
                    billRecord.amount=999999.0
                    add_show_now_money.text= "${resources.getString(R.string.currency)}${billRecord.amount}"
                    add_input_money.setText("999999")
                    add_input_money.setSelection(add_input_money.text.length)
                }
            } catch (e : NumberFormatException) {
                if(billRecord.amount>0){
                    add_show_now_money.text= "${resources.getString(R.string.currency)} ${billRecord.amount}"
                } else {
                    add_input_money.setText("")
                }

            }
        } else {
            shouldInputMoneyChange=true
        }


    }

    /**
     * 改变账目类型时发生的事件，将会改变
     * @see BillRecord.ioType
     */
    override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
        when(checkedId){
            R.id.add_bill_radio_income -> {
                setData(IOType.Income)
                adapter.source = data
                adapter.notifyDataSetChanged()
            }
            R.id.add_bill_radio_outcome -> {
                setData(IOType.Outcome)
                adapter.source = data
                adapter.notifyDataSetChanged()
            }
            R.id.add_bill_radio_others -> {
                setData(IOType.Other)
                adapter.source = data
                adapter.notifyDataSetChanged()
            }
            else -> {
                Log.i(TAG, checkedId.toString())
            }
        }
    }

    /**
     * 当选中的时间改变时
     */
    override fun getDate(date: Date) {
        //isDateChoose=true
        billRecord.time=date
        add_bill_show_date.text=DateConverter.getSimpleString(date)
        Log.d(TAG, DateConverter.getString(billRecord.time!!))
    }

    //endregion

    //region 工具函数
    /**
     * 用于初始化页面，特别是修改已有的账目记录时
     */
    private fun setRecord(record:BillRecord)
    {
        add_bill_ed_remark.setText(record.remark)

        add_bill_show_date.text = DateConverter.getSimpleString(
            if (record.time == null){
                Date()
            } else {
                //Log.d(TAG,"time::${DateConverter.getSimpleString(record.time!!)}")
                record.time!!
            }
        )

        add_bill_radio_group.check(when(record.ioType){
            IOType.Outcome -> R.id.add_bill_radio_outcome
            IOType.Income -> R.id.add_bill_radio_income
            IOType.Other -> R.id.add_bill_radio_others
            IOType.Unset -> R.id.add_bill_radio_outcome
        })

        //选中图标
        for (p in data){
            p["chosen"] = p["class"] == record.goodsType
        }

        //add_input_money.setText("${record.amount}")

        add_show_now_money.text="${resources.getString(R.string.currency)} ${record.amount}"

        billRecord = record
    }

    /**
     * 设置
     * @see data 为指定的类型，主要用于和
     * @see adapter 进行交互
     */
    private fun setData(type:IOType){
        billRecord.ioType = type
        data = when(type){
            IOType.Outcome->IOTypeProvider(this).outcomeTypeList
            IOType.Income->IOTypeProvider(this).incomeTypeList
            IOType.Other->IOTypeProvider(this).othersTypeList
            else -> IOTypeProvider(this).outcomeTypeList
        }
    }
    //endregion

    companion object {
        const val TAG = "ABA"
    }
}
