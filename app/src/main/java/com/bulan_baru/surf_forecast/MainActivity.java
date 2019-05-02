package com.bulan_baru.surf_forecast;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;


import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.Forecast;
import com.bulan_baru.surf_forecast_data.Location;
import com.bulan_baru.surf_forecast_data.SurfForecastRepository;
import com.bulan_baru.surf_forecast_data.SurfForecastService;
import com.bulan_baru.surf_forecast_data.utils.Logger;
import com.bulan_baru.surf_forecast_data.utils.Spinner2;
import com.bulan_baru.surf_forecast_data.utils.TypeConverter;
import com.bulan_baru.surf_forecast_data.utils.UncaughtExceptionHandler;
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends GenericActivity{
    private static final String LOG_TAG = "Main";

    private int currentLocationID;
    private Forecast currentForecast;
    private SurfConditionsFragmentAdapter surfConditionsAdapter;
    private Calendar forecastLastDisplayed;
    private int currentConditionsPage = -1;
    private Calendar pauseLocationUpdates;
    private boolean noForecastForLocationError = false;
    private int lastShownErrorCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        surfConditionsAdapter = new SurfConditionsFragmentAdapter(this, getSupportFragmentManager());

        hideSurfConditions();
        hideProgress();

        if (!viewModel.isUsingDeviceLocation()) {
            //then we need a timer
            onDeviceLocationUpdated(null);
            startTimer(30);
        }
    }

    @Override
    protected void onTimer(){
        onDeviceLocationUpdated(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        hideSurfConditions();
    }


    @Override
    public void onRestart(){
        super.onRestart();

        showSurfConditions();
    }

    @Override
    public void showError(int errorCode, String errorMessage){
        Log.e("GAERROR", errorMessage);
        if(errorCode == SurfForecastRepository.ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE){
            noForecastForLocationError = true;
        }


        //suppress service unreachable errors if they occur when just refreshing data and are less than an hour old
        if(errorCode == SurfForecastRepository.ERROR_SERVICE_UNREACHABLE){
            boolean isSameLocation = (currentForecast != null && currentForecast.getLocationID() == currentLocationID);
            if(isSameLocation && forecastLastDisplayed != null && Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.MINUTES) < 60){
                Logger.error("Suppressed error: " + errorMessage);
                return;
            } else if(isSameLocation && forecastLastDisplayed != null){
                errorMessage += "Forecast last displayed " + Utils.formatDate(forecastLastDisplayed, SurfForecastService.DATE_FORMAT);
            }
        }

        lastShownErrorCode = errorCode;
        Logger.error("Shown error: " + errorMessage);
        super.showError(errorCode, errorMessage);
    }

    private void showSurfConditions(int visibility){
        ViewPager viewPager = findViewById(R.id.viewPager);
        viewPager.setVisibility(visibility);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setVisibility(visibility);
    }

    private void showSurfConditions(){ showSurfConditions(View.VISIBLE); }
    private void hideSurfConditions(){ showSurfConditions(View.INVISIBLE); }


    /*
        Assign the particular view model and call the parent
     */
    @Override
    protected void initViewModel(Bundle savedInstanceState){
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        super.initViewModel(savedInstanceState);
    }


    @Override
    public void onDeviceLocationUpdated(ClientDevice device){
        if(pauseLocationUpdates != null && Utils.dateDiff(Calendar.getInstance(), pauseLocationUpdates, TimeUnit.SECONDS) < 30){
            Log.i(LOG_TAG,"Location updates paused");
            return;
        }

        ((MainViewModel)viewModel).getLocationsNearby().observe(this, locations -> {
            //we have a successful return so if there was a service unreachable error earlier that is still showing
            //then we can dismiss it here
            if(isErrorShowing() && lastShownErrorCode == SurfForecastRepository.ERROR_SERVICE_UNREACHABLE){
                dismissError();
            }

            //now fill in the locations
            if(locations.size() > 0) {
                TypeConverter<Location,String> tc = new TypeConverter<Location,String>(){
                    @Override
                    public String convert(Location loc){
                        String dist = Utils.convert(loc.getDistance(), Utils.Conversions.KM_2_MILES, 1);
                        return loc.getLocation() + " (" + dist + " miles)";
                    }
                };
                List<String> spinnerList = tc.convertList(locations);

                Spinner2 locationsSpinnner = findViewById(R.id.surfLocation);
                if(!locationsSpinnner.isOpen()) {

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, spinnerList);
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown);
                    locationsSpinnner.setAdapter(adapter);

                    locationsSpinnner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                            if(position < locations.size()) {
                                pauseLocationUpdates = position == 0 ? null : Calendar.getInstance();
                                getForecastForLocation(locations.get(position).getID());
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            // your code here
                        }
                    });
                }
            }
        });
    }

    protected void getForecastForLocation(int locationID){
        //if the current forecast is for the same location then don't immediately go and get a new forecast
        //instead wait X minutes before doing so
        if(currentForecast != null && currentConditionsPage == 0 && currentForecast.getLocationID() == locationID && Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.MINUTES) < 20){
            return;
        }

        //if there is a request to get the forecast for the same location that produced a noForecastForLocation error then return instead
        if(currentLocationID == locationID && noForecastForLocationError){
            return;
        }

        currentLocationID = locationID;
        noForecastForLocationError = false;
        hideSurfConditions();
        showProgress();
        ((MainViewModel)viewModel).getForecast(currentLocationID).observe(this, forecast -> {

            setCurrentForecast(forecast);

        });
    }

    protected void setCurrentForecast(Forecast forecast){
        //check if the request to set the current forecast doesn't require changing the data displayed
        //this differs from the wait in getForecastForLocation because it tests whether the feed run is the same
        //if it is the same then the data will be the same and there's no need for updating display
        boolean locationHasChanged = (currentForecast != null && currentForecast.getLocationID() != forecast.getLocationID());
        boolean forecastHasChanged = (currentForecast == null || currentForecast.getFeedRunID() != forecast.getFeedRunID() || locationHasChanged);
        Calendar now = Calendar.getInstance();
        if(!forecastHasChanged && Utils.dateDiff(now, forecastLastDisplayed, TimeUnit.MINUTES) < 25 && currentConditionsPage == 0){
            showSurfConditions();
            hideProgress();
            return;
        }

        //if here the forecast being set is different from the one that is already set ... or it's the first forecast being set
        //... or a certain time has elapsed
        String s = "Updated ";
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Calendar cal = Calendar.getInstance(tz);
        long hoursDiff = Utils.dateDiff(cal, Utils.date2cal(forecast.getCreated()), TimeUnit.HOURS);
        if(hoursDiff <= 0){
            s+= "just now";
        } else if (hoursDiff < 24) {
            s+= Utils.round2string(hoursDiff, 0);
            s+= hoursDiff == 1 ? " hour" : " hours";
            s+= " ago";
        } else if(hoursDiff >= 24){
            double days = Math.floor(hoursDiff/24);
            hoursDiff = hoursDiff - (long)days*24;
            s += Utils.round2string(days, 0) + (days > 1 ? " days" : " day");
            s += (hoursDiff > 0 ? " and " + hoursDiff + " hours" : "") + " ago";
        }

        boolean expired = Utils.dateDiff(cal, forecast.getForecastTo()) > 0;
        if(expired){
            s = "Forecast expired ... " + s;
        }


        if(forecast.now().getTimeZone().getRawOffset() != Calendar.getInstance().getTimeZone().getRawOffset()){
            String stz = Utils.formatDate(forecast.now(), "Z");
            s = "Forecast times @ " + stz + " GMT ........ " + s;
        }

        TextView forecastInfo = findViewById(R.id.forecastInfo);
        forecastInfo.setText(s);

        hideProgress();
        if(!expired) {
            ViewPager viewPager = findViewById(R.id.viewPager);
            currentConditionsPage = viewPager.getCurrentItem();

            surfConditionsAdapter.setForecast(forecast);
            if (currentForecast == null) { //first time this is ever called
                //get the view pager for the surf conditions
                viewPager.setAdapter(surfConditionsAdapter);
                viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(int i, float v, int i1) {

                    }

                    @Override
                    public void onPageSelected(int i) {
                        pauseLocationUpdates = Calendar.getInstance();
                        currentConditionsPage = i;
                    }

                    @Override
                    public void onPageScrollStateChanged(int i) {

                    }
                });

                TabLayout tabLayout = findViewById(R.id.tabLayout);
                tabLayout.setupWithViewPager(viewPager);
            } else if(pauseLocationUpdates == null) {
                viewPager.setCurrentItem(0);
            } else {
                viewPager.setCurrentItem(currentConditionsPage);
            }

            showSurfConditions();
        }

        currentForecast = forecast;
        forecastLastDisplayed = now;

        Logger.info("Forecast displayed for location ID " + forecast.getLocationID());
    }
}
