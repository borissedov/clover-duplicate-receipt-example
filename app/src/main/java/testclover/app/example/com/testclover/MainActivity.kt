package testclover.app.example.com.testclover

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.clover.sdk.util.CloverAccount
import com.clover.sdk.v1.Intents
import com.clover.sdk.v1.printer.job.StaticReceiptPrintJob
import com.clover.sdk.v3.order.LineItem
import com.clover.sdk.v3.order.Order
import com.clover.sdk.v3.order.OrderConnector
import com.clover.sdk.v3.payments.Payment
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var orderConnector: OrderConnector? = null
    private var account: Account? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        account = CloverAccount.getAccount(this)

        button.setOnClickListener {
            createOrder { order ->
                val intent = Intent(Intents.ACTION_SECURE_PAY)
                intent.putExtra(Intents.EXTRA_ORDER_ID, order.id)
                intent.putExtra(Intents.EXTRA_AMOUNT, 5000.toLong())
                intent.putExtra(Intents.EXTRA_CARD_ENTRY_METHODS, Intents.CARD_ENTRY_METHOD_ALL)
                startActivityForResult(intent, 1)
            }
        }

    }

    override fun onResume() {
        super.onResume()

        connectOrder()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) when (requestCode) {
            1 -> {
                val payment = data!!.getParcelableExtra<Payment>(Intents.EXTRA_PAYMENT)

                getOrderClover(payment.order.id) {
                    StaticReceiptPrintJob.Builder().order(it).build().print(this, account)
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    fun getOrderClover(orderId: String, callback: (order: Order) -> Unit) {
        if (orderConnector != null) {
            object : AsyncTask<Void, Void, Order>() {
                override fun doInBackground(vararg params: Void): Order? {
                    return orderConnector!!.getOrder(orderId)
                }

                override fun onPostExecute(order: Order?) {
                    if (order == null || !order.hasPayments()) {
                        getOrderClover(orderId, callback)
                        return
                    } else {
                        callback(order)
                    }
                }
            }.execute()
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun createOrder(callback: (order: Order) -> Unit) {
        object : AsyncTask<Void, Void, Order>() {
            override fun doInBackground(vararg params: Void): Order? {
                return createOrder()
            }

            override fun onPostExecute(order: Order?) {
                if (order == null) {
                    return
                }

                callback(order)
            }
        }.execute()
    }

    fun createOrder(): Order? {
        try {
            val newlineItems = arrayListOf<LineItem>()

            val order = orderConnector!!.createOrder(Order())

            var customLineItem = LineItem()
                    .setName("customLineItem")
                    .setAlternateName("")
                    .setPrice(5000.toLong())
                    .setUnitQty(1)


            customLineItem = orderConnector!!.addCustomLineItem(order.id, customLineItem, false)
            newlineItems.add(customLineItem)

            val lineItems: MutableList<LineItem>
            lineItems = if (order.hasLineItems()) {
                ArrayList(order.lineItems)
            } else {
                java.util.ArrayList()
            }

            lineItems.addAll(newlineItems)
            order.lineItems = lineItems

            return orderConnector!!.getOrder(order.id)
        } catch (e: Exception) {
            Log.w("clover", "create order failed", e)
        }
        return null
    }

    private fun connectOrder() {
        if (account != null) {
            orderConnector = OrderConnector(this, account, null)
            orderConnector!!.connect()
        }
    }
}
