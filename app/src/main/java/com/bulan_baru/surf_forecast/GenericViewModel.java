package com.bulan_baru.surf_forecast;

//import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.location.Location;

import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.Digest;
import com.bulan_baru.surf_forecast_data.ServerStatus;
import com.bulan_baru.surf_forecast_data.SurfForecastRepository;
import com.bulan_baru.surf_forecast_data.SurfForecastRepositoryException;

public class GenericViewModel extends ViewModel {
    protected SurfForecastRepository surfForecastRepository;

    public GenericViewModel() {

    }

    void init(SurfForecastRepository surfForecastRepository) {
        this.surfForecastRepository = surfForecastRepository;
    }

    SurfForecastRepository getSurfForecastRepository(){ return surfForecastRepository; }

    ClientDevice getClientDevice() {
        return surfForecastRepository.getClientDevice();
    }
    LiveData<ClientDevice> clientDevice(){ return surfForecastRepository.clientDevice(); }

    boolean isUsingDeviceLocation(){ return surfForecastRepository.isUsingDeviceLocation(); }

    LiveData<ServerStatus> getServerStatus(){ return surfForecastRepository.getServerStatus(); }

    ServerStatus getLastServerStatus(){ return surfForecastRepository.getLastServerStatus(); }

    LiveData<SurfForecastRepositoryException> repositoryError(){ return surfForecastRepository.repositoryError(); }

    LiveData<Digest> postDigest(Digest digest){ return surfForecastRepository.postDigest(digest); }
}
