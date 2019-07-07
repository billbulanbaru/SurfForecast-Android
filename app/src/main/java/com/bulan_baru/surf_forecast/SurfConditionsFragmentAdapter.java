package com.bulan_baru.surf_forecast;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.util.Log;

import com.bulan_baru.surf_forecast_data.Forecast;
import com.bulan_baru.surf_forecast_data.ForecastDay;
import com.bulan_baru.surf_forecast_data.ForecastHour;
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SurfConditionsFragmentAdapter extends FragmentPagerAdapter {

    private final static int STEPS = 3;
    private final static int MIN_INTERVAL = 4;
    private final static int MAX_INTERVAL = 6;


    private long baseTimeForId;
    private Context context;
    private Forecast forecast;
    private List<Calendar> dates;
    private List<List<ForecastHour>> hourSpreads;


    public SurfConditionsFragmentAdapter(Context context, FragmentManager fm) {
        super(fm);
        this.context = context;
        Calendar base = Utils.calendarZeroTime(Calendar.getInstance());
        base.set(Calendar.YEAR, 2018);
        base.set(Calendar.MONTH, 1);
        base.set(Calendar.DATE, 1);

        baseTimeForId = base.getTimeInMillis();

    }

    public void setForecast(Forecast forecast){
        this.forecast = forecast;

        //use forecast period to set the 'days' the view can see
        //start by getting some important dates
        Calendar startDate = forecast.now(); //get 'now' with forecast timezone applied
        if(forecast.now().getTimeInMillis() < forecast.getForecastFrom().getTimeInMillis()){
            startDate = Utils.calendarSetHour(forecast.getForecastFrom(), startDate.get(Calendar.HOUR_OF_DAY));
        }
        Calendar firstLight = forecast.getFirstLight(startDate);
        Calendar lastLight = forecast.getLastLight(startDate);
        Calendar tomorrow = (Calendar)startDate.clone();
        tomorrow.add(Calendar.DATE, 1);

        int minInterval = MIN_INTERVAL;
        //check if the forecast is for today or tomorrow
        if(startDate.compareTo(firstLight) < 0){ //before first light then we want forecast from first light
            startDate = firstLight;
            startDate = Utils.calendarSetHour(startDate, startDate.get(Calendar.HOUR_OF_DAY) + 1);
        } else if(startDate.compareTo(lastLight) > 0){ //after last light so we want forecast from first light tomorrow
            startDate = forecast.getFirstLight(tomorrow);
            startDate = Utils.calendarSetHour(startDate, startDate.get(Calendar.HOUR_OF_DAY) + 1);
        } else { //we are somewhere between first light and last light today
            minInterval = Math.min((int)Utils.dateDiff(lastLight, startDate, TimeUnit.HOURS), MIN_INTERVAL);
            startDate = Utils.calendarSetHour(startDate, startDate.get(Calendar.HOUR_OF_DAY) + 1);
        }

        hourSpreads = new ArrayList<>();
        hourSpreads.add(forecast.getHoursSpread(startDate, STEPS, minInterval, MAX_INTERVAL));

        //sanitise remaining days so they all start from first light
        dates = Utils.getDates(startDate, forecast.getForecastTo());
        for(int i = 1; i < dates.size(); i++){
            firstLight = forecast.getFirstLight(dates.get(i));
            dates.set(i, Utils.calendarSetHour(firstLight, firstLight.get(Calendar.HOUR_OF_DAY) + 1));
            hourSpreads.add(forecast.getHoursSpread(dates.get(i), STEPS, MIN_INTERVAL, MAX_INTERVAL));
        }
        notifyDataSetChanged();
    }


    // This determines the fragment for each tab
    @Override
    public Fragment getItem(int position) {
        SurfConditionsFragment scf = new SurfConditionsFragment();

        Calendar cal = dates.get(position);
        scf.setForecast(forecast, cal, hourSpreads.get(position));
        return scf;
    }

    @Override
    public long getItemId(int position){
        Log.i("SCFA", "getItemId");
        Calendar cal = dates.get(position);
        long mins = (cal.getTimeInMillis() - baseTimeForId)/60000;
        mins *= 10000000;
        long itemID = mins + (long)forecast.getFeedRunID();
        itemID *= 1000000;
        itemID += (long)forecast.getLocationID();
        return itemID;
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }


    // This determines the number of tabs
    @Override
    public int getCount() {
        return dates.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Calendar cal = dates.get(position);
        String pageTitle = Utils.formatDate(cal, "EEE, d MMM");
        if(position == 0) {
            pageTitle = Utils.isToday(cal, forecast.now()) ? "Today" : "Tomorrow";
        } else if(position == 1 && Utils.isToday(dates.get(0), forecast.now())){
            pageTitle = "Tomorrow";
        }
        return pageTitle;
    }

}
