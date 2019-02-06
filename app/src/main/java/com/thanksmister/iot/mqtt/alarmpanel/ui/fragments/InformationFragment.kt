/*
 * Copyright (c) 2018 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thanksmister.iot.mqtt.alarmpanel.ui.fragments

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.os.Handler
import android.os.Looper.getMainLooper
import android.support.v4.content.res.ResourcesCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.thanksmister.iot.mqtt.alarmpanel.BaseActivity
import com.thanksmister.iot.mqtt.alarmpanel.BaseFragment
import com.thanksmister.iot.mqtt.alarmpanel.R
import com.thanksmister.iot.mqtt.alarmpanel.network.model.Datum
import com.thanksmister.iot.mqtt.alarmpanel.persistence.Configuration
import com.thanksmister.iot.mqtt.alarmpanel.utils.DialogUtils
import com.thanksmister.iot.mqtt.alarmpanel.viewmodel.WeatherViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_information.*
import java.text.DateFormat
import java.util.*
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thanksmister.iot.mqtt.alarmpanel.network.DarkSkyOptions
import com.thanksmister.iot.mqtt.alarmpanel.network.DarkSkyRequest
import com.thanksmister.iot.mqtt.alarmpanel.utils.WeatherUtils
import timber.log.Timber

class InformationFragment : BaseFragment() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var weatherViewModel: WeatherViewModel

    @Inject lateinit var configuration: Configuration
    @Inject lateinit var dialogUtils: DialogUtils
    @Inject lateinit var darkSkyOptions: DarkSkyOptions

    private var forecastList: List<Datum> = Collections.emptyList()
    private var timeHandler: Handler? = null

    private val timeRunnable = object : Runnable {
        override fun run() {
            val currentDateString = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date())
            val currentTimeString = DateUtils.formatDateTime(context, Date().time, DateUtils.FORMAT_SHOW_TIME)
            dateText.text = currentDateString
            timeText.text = currentTimeString
            timeHandler?.postDelayed(this, 1000)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        weatherViewModel = ViewModelProviders.of(this, viewModelFactory).get(WeatherViewModel::class.java)
        observeViewModel(weatherViewModel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timeHandler = Handler(getMainLooper())
        timeHandler?.postDelayed(timeRunnable, 1000)
        weatherLayout.visibility = View.VISIBLE
        weatherLayout.setOnClickListener {
            if (!forecastList.isEmpty()) {
                dialogUtils.showExtendedForecastDialog(activity as BaseActivity, forecastList)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_information, container, false)
    }

    override fun onResume() {
        super.onResume()
        if (configuration.showWeatherModule() && darkSkyOptions.isValid) {
            connectWeatherModule()
            weatherLayout.visibility = View.VISIBLE
        } else {
            weatherViewModel.onCleared()
            weatherLayout.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        weatherViewModel.onCleared()
    }

    override fun onDetach() {
        super.onDetach()
        timeHandler?.removeCallbacks(timeRunnable)
    }

    private fun observeViewModel(viewModel: WeatherViewModel) {
        activity?.let {
            viewModel.getAlertMessage().observe(this, Observer { message ->
                Timber.d("getAlertMessage")
                dialogUtils.showAlertDialog(it, message!!)
            })
            viewModel.getToastMessage().observe(this, Observer { message ->
                Timber.d("getToastMessage")
                Toast.makeText(it, message, Toast.LENGTH_LONG).show()
            })
            disposable.add(
                    viewModel.getLatestItem()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe ({ item ->
                                if(item != null) {
                                    weatherLayout.visibility = View.VISIBLE
                                    outlookText.text = item.summary
                                    val displayUnits = if (item.units == DarkSkyRequest.UNITS_US) getString(R.string.text_f) else getString(R.string.text_c)
                                    temperatureText.text = getString(R.string.text_temperature, item.apparentTemperature, displayUnits)
                                    forecastList = Gson().fromJson(item.data, object : TypeToken<List<Datum>>(){}.type)
                                    try {
                                        if (item.umbrella) {
                                            conditionImage.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_rain_umbrella, (it as BaseActivity).theme))
                                        } else {
                                            conditionImage.setImageDrawable(ResourcesCompat.getDrawable(resources, WeatherUtils.getIconForWeatherCondition(item.icon), (it as BaseActivity).theme))
                                        }
                                    } catch (e : OutOfMemoryError) {
                                        Timber.e(e.message)
                                    }
                                }
                            }, { error -> Timber.e("weather drawable error" + error.message) })
            )
        }
    }

    private fun connectWeatherModule() {
        val apiKey = darkSkyOptions.darkSkyKey
        val units = darkSkyOptions.weatherUnits
        val lat = darkSkyOptions.latitude
        val lon = darkSkyOptions.longitude
        weatherViewModel.getDarkSkyHourlyForecast(apiKey!!, units, lat!!, lon!!)
    }

    companion object {
        fun newInstance(): InformationFragment {
            return InformationFragment()
        }
    }
}