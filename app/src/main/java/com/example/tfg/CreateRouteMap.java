package com.example.tfg;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.example.tfg.adapters.PlaceAdapter;
import com.example.tfg.utils.GetMapsInformation;
import com.example.tfg.utils.Functions;
import com.example.tfg.utils.URLConstructor;
import com.example.tfg.interfaces.ItemListener;
import com.example.tfg.objects.Lugar;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CreateRouteMap extends AppCompatActivity implements
        OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMarkerClickListener, View.OnClickListener,
        GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener {

    private GoogleMap myMap;
    private FusedLocationProviderClient myFusedLocationProviderClient;
    private Location myLastKnownLocation;
    private LatLng miPosicionOriginal;
    private LatLng miPosicionDeCamaraCambiante;
    private HashMap<LatLng, String[]> placesMap = new HashMap<>();
    private HashMap<String, String[]> photosMap = new HashMap<>();

    //Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private final int LOCATION_REQUEST_CODE = 1001;

    //Places
    private PlacesClient placesClient;
    private List<Place.Field> campos = Arrays.asList(Place.Field.NAME, Place.Field.ID, Place.Field.LAT_LNG,
            Place.Field.PHONE_NUMBER, Place.Field.WEBSITE_URI);
    private Place place;

    //Ruta
    private ArrayList<Lugar> places = new ArrayList<>();
    private Button crearRuta;
    private RecyclerView vista;

    //Filtros de búsqueda
    final String[] tipos = {"que_ver", "bar", "night_club", "library", "cafe", "casino", "shopping_mall", "movie_theater",
            "art_gallery", "hospital", "lodging", "book_store", "liquor_store", "subway_station", "museum", "park",
            "bakery", "police", "restaurant", "supermarket", "taxi_stand", "clothing_store", "university", "zoo"};

    private Spinner tipoLugar;
    private EditText radioBusqueda;
    private String tipoCadena;
    private Double radioNumero;
    private Button buscar;
    private FrameLayout mapInicio;

    //Tipo de razón de cambio de la cámara
    private int reason;

    //Vista del lugar elegido
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog alertDialog;

    private Boolean fromFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Restaurar posición actual y cámara si entro por vez > 1ª
        if (savedInstanceState != null) {
            myLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
        }

        setContentView(R.layout.activity_create_route_map);

        // Initialize the SDK
        Places.initialize(getApplicationContext(), getString(R.string.GOOGLE_MAPS_API_KEY));
        // Create a new PlacesClient instance
        placesClient = Places.createClient(CreateRouteMap.this);

        //Para obtener la ubicación actual
        myFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(CreateRouteMap.this);

        //Botón para lanzar Intent y crear ruta
        crearRuta = findViewById(R.id.createRoute);
        crearRuta.setOnClickListener(this);
        fromFragment = getIntent().getExtras().getBoolean("flag");

        //Spinner con tipo de lugar, input de radio de búsqueda y botón
        tipoLugar = findViewById(R.id.lugaresDisponibles);
        radioBusqueda = findViewById(R.id.radioBusqueda);
        buscar = findViewById(R.id.buscarButton);
        buscar.setOnClickListener(this);
        mapInicio = findViewById(R.id.mapFrame);
        vista = findViewById(R.id.listaPlaces);

        //Comprobar permisos y construir mapa
        if (ContextCompat.checkSelfPermission(
                CreateRouteMap.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    CreateRouteMap.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            cargaMapa();
        }
        tipoLugar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                setInputsView();
                tipoLugar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void setInputsView(){
        int density = (int) CreateRouteMap.this.getResources().getDisplayMetrics().density;
        int pxMargins = 10 * density;
        int widthUsable = CreateRouteMap.this.getResources().getDisplayMetrics().widthPixels - 2*pxMargins;
        int tamTipo = 40*widthUsable/100;
        int tamRadio = 30*widthUsable/100;
        int tamBuscar = 30*widthUsable/100;
        ConstraintLayout.LayoutParams constraintsTIPO = new ConstraintLayout.LayoutParams(tamTipo, 45*density);
        constraintsTIPO.topToTop = R.id.parent;
        constraintsTIPO.leftToLeft = R.id.parent;
        tipoLugar.setBackground(CreateRouteMap.this.getDrawable(R.drawable.borders));
        tipoLugar.setLayoutParams(constraintsTIPO);
        ConstraintLayout.LayoutParams constraintsRADIO = new ConstraintLayout.LayoutParams(tamRadio, 45*density);
        constraintsRADIO.topToTop = R.id.parent;
        constraintsRADIO.leftToRight = R.id.lugaresDisponibles;
        constraintsRADIO.setMarginStart(pxMargins/2);
        radioBusqueda.setBackground(CreateRouteMap.this.getDrawable(R.drawable.borders));
        radioBusqueda.setLayoutParams(constraintsRADIO);
        ConstraintLayout.LayoutParams constraintsBUSCAR = new ConstraintLayout.LayoutParams(tamBuscar, 45*density);
        constraintsBUSCAR.topToTop = R.id.parent;
        constraintsBUSCAR.leftToRight = R.id.radioBusqueda;
        constraintsBUSCAR.setMarginStart(pxMargins/2);
        buscar.setBackground(CreateRouteMap.this.getDrawable(R.drawable.button_styles));
        buscar.setLayoutParams(constraintsBUSCAR);
        buscar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                setMapView();
                buscar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void setMapView(){
        int pxMargins = (int) (10 * CreateRouteMap.this.getResources().getDisplayMetrics().density);
        int tipoInicio = (int) tipoLugar.getY();
        int tipoTAM = tipoLugar.getHeight();
        int crearInicio = (int) crearRuta.getY();
        int usableScreen = crearInicio - (tipoInicio + tipoTAM);
        int mapScreen = 60*usableScreen/100;
        ConstraintLayout.LayoutParams constraintsParamsMAP = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mapScreen);
        constraintsParamsMAP.topToBottom = R.id.lugaresDisponibles;
        constraintsParamsMAP.leftToLeft = R.id.parent;
        constraintsParamsMAP.topMargin = pxMargins / 2;
        mapInicio.setBackground(CreateRouteMap.this.getDrawable(R.drawable.borders));
        mapInicio.setLayoutParams(constraintsParamsMAP);
        mapInicio.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                setListView();
                mapInicio.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void setListView(){
        if(!fromFragment){
            places = getIntent().getExtras().getParcelableArrayList("places");
            mostrarDatos(places);
        }
        int pxMargins = (int) (10 * CreateRouteMap.this.getResources().getDisplayMetrics().density);
        int listInicio = (int) vista.getY();
        int buttonInicio = (int) crearRuta.getY();
        int usableSpace = buttonInicio - listInicio - pxMargins;
        ConstraintLayout.LayoutParams constraintsParams = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, usableSpace);
        constraintsParams.topToBottom = R.id.mapFrame;
        constraintsParams.leftToLeft = R.id.parent;
        constraintsParams.topMargin = pxMargins / 2;
        vista.setBackground(CreateRouteMap.this.getDrawable(R.drawable.borders));
        vista.setLayoutParams(constraintsParams);
    }

    //Guardar posición actual y cámara al acceder por 1ª vez
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (myMap != null) {
            outState.putParcelable(KEY_LOCATION, myLastKnownLocation);
            outState.putParcelable(KEY_CAMERA_POSITION, myMap.getCameraPosition());
            super.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cargaMapa();
            } else {
                Toast.makeText(CreateRouteMap.this, getString(R.string.error_ubicacion), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void cargaMapa() {
        // Get the SupportMapFragment and request notification
        // when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        myMap = googleMap;
        //myMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.style_json));
        if (ContextCompat.checkSelfPermission(
                CreateRouteMap.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    CreateRouteMap.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        myMap.setMyLocationEnabled(true);
        myMap.setOnMyLocationButtonClickListener(this);
        myMap.setOnMarkerClickListener(this);
        myMap.setOnCameraMoveStartedListener(this);
        myMap.setOnCameraIdleListener(this);
        getPosicionActual();
    }

    private void getPosicionActual(){
        if (ContextCompat.checkSelfPermission(
                CreateRouteMap.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    CreateRouteMap.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        Task<Location> locationResult = myFusedLocationProviderClient.getLastLocation();
        locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                if (task.isSuccessful()) {
                    // Get the current location of the device.
                    if (task.getResult() != null) {
                        myLastKnownLocation = task.getResult();
                        miPosicionOriginal = new LatLng(myLastKnownLocation.getLatitude(), myLastKnownLocation.getLongitude());
                        miPosicionDeCamaraCambiante = miPosicionOriginal;
                        myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                miPosicionDeCamaraCambiante, 15));
                        myMap.addMarker(new MarkerOptions().position(miPosicionDeCamaraCambiante)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                    } else {
                        Toast.makeText(CreateRouteMap.this, getString(R.string.error_obt_ubicacion), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Toast.makeText(CreateRouteMap.this, getString(R.string.error_obt_ubicacion), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
    }

    private void busca(String tipo, Double radio){
        myMap.clear();
        myMap.addMarker(new MarkerOptions().position(miPosicionDeCamaraCambiante)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        String nearbyPlacesUrl = URLConstructor.getUrlNearbyPlaces(
                miPosicionDeCamaraCambiante, tipo, radio, getString(R.string.GOOGLE_MAPS_API_KEY));
        Object[] dataTransfer = new Object[2];
        dataTransfer[0] = myMap;
        dataTransfer[1] = nearbyPlacesUrl;
        GetMapsInformation getMapsInformation = new GetMapsInformation(dataTransfer, GetMapsInformation.MapDataType.PLACE);
        getMapsInformation.execute();
        placesMap = getMapsInformation.getPlacesMap();
        photosMap = getMapsInformation.getPhotosMap();
    }

    private void recuperaDetallesLugar(String id){
        final FetchPlaceRequest request = FetchPlaceRequest.newInstance(id, campos);
        placesClient.fetchPlace(request).addOnSuccessListener(new OnSuccessListener<FetchPlaceResponse>() {
            @Override
            public void onSuccess(FetchPlaceResponse fetchPlaceResponse) {
                place = fetchPlaceResponse.getPlace();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(CreateRouteMap.this, getString(R.string.error_enc_lugar), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onMyLocationButtonClick() {
        myMap.clear();
        myMap.addMarker(new MarkerOptions().position(miPosicionOriginal)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(myLastKnownLocation.getLatitude(), myLastKnownLocation.getLongitude()),15));
        return false;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if(!Functions.compruebaExistencia(marker.getPosition(), places)){
            if(!marker.getPosition().equals(miPosicionOriginal) && !marker.getPosition().equals(miPosicionDeCamaraCambiante)){
                String place_id = placesMap.get(marker.getPosition())[0];
                String place_name = placesMap.get(marker.getPosition())[1];
                String place_address = placesMap.get(marker.getPosition())[2];
                String open = getString(R.string.sin_horario);
                if(!placesMap.get(marker.getPosition())[3].equals("-NA-")){
                    if(placesMap.get(marker.getPosition())[3].equals("true")){
                        open = getString(R.string.abierto_ahora);
                    }else if(placesMap.get(marker.getPosition())[3].equals("false")){
                        open = getString(R.string.cerrado_ahora);
                    }
                }
                String[] photo = photosMap.get(place_id);
                String photoUrl;
                if(photo[0].equals("-NA-")){
                    photoUrl = "NOPHOTO";
                }else{
                    photoUrl = URLConstructor.getUrlPhoto(photo, getString(R.string.GOOGLE_MAPS_API_KEY));
                }
                recuperaDetallesLugar(place_id);
                mostrarLugar(marker.getPosition(), place_name, place_id, place_address, open, photoUrl);
            }
        }else{
            Toast.makeText(CreateRouteMap.this, getString(R.string.error_lugar_ya_agregado), Toast.LENGTH_LONG).show();
        }
        return false;
    }

    //Carga la vista con los datos del lugar
    private void mostrarLugar(final LatLng posicion, final String title, final String id, String direccion, String abierto, String photoUrl){
        alertDialogBuilder = new AlertDialog.Builder(this);
        final View vistaLugar = getLayoutInflater().inflate(R.layout.vista_agregar_lugar, null);

        ImageView fotoLugar = vistaLugar.findViewById(R.id.fotoLugar);
        TextView nombreLugar = vistaLugar.findViewById(R.id.nombreLugar);
        TextView direccionLugar = vistaLugar.findViewById(R.id.direccionLugar);
        TextView open = vistaLugar.findViewById(R.id.estaAbierto);
        ImageView tlf = vistaLugar.findViewById(R.id.imagenTlf);
        tlf.setClickable(true);
        tlf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(place.getPhoneNumber()!=null){
                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                    callIntent.setData(Uri.parse("tel:"+place.getPhoneNumber()));
                    startActivity(callIntent);
                }else{
                    Toast.makeText(CreateRouteMap.this, getString(R.string.error_sin_numero_tlf), Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageView web = vistaLugar.findViewById(R.id.imagenWeb);
        web.setClickable(true);
        web.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(place.getWebsiteUri()!=null){
                    Intent webIntent = new Intent(Intent.ACTION_VIEW);
                    webIntent.setData(place.getWebsiteUri());
                    startActivity(webIntent);
                }else{
                    Toast.makeText(CreateRouteMap.this, getString(R.string.error_sin_sitio_web), Toast.LENGTH_SHORT).show();
                }
            }
        });

        if(photoUrl.equals("NOPHOTO")){
            Glide.with(CreateRouteMap.this)
                    .load(R.drawable.no_image)
                    .into(fotoLugar);
        }else{
            Glide.with(CreateRouteMap.this)
                    .load(Uri.parse(photoUrl))
                    .into(fotoLugar);
        }
        nombreLugar.setText(title);
        direccionLugar.setText(direccion);
        open.setText(abierto);

        Button agregarLugar = vistaLugar.findViewById(R.id.agregarLugar);
        Button cancelarLugar = vistaLugar.findViewById(R.id.cancelarLugar);
        agregarLugar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(places.size()<10){
                    places.add(new Lugar(id, posicion, title));
                    mostrarDatos(places);
                    Toast.makeText(CreateRouteMap.this, getString(R.string.lugar_agregado), Toast.LENGTH_LONG).show();
                    alertDialog.dismiss();
                }else{
                    Toast.makeText(CreateRouteMap.this, getString(R.string.error_10_lugares), Toast.LENGTH_LONG).show();
                    alertDialog.dismiss();
                }
            }
        });
        cancelarLugar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
            }
        });

        alertDialogBuilder.setView(vistaLugar);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    //Confirmar eliminar lugar de ruta
    private void confirmaEliminarLugar(String nombre, final int posicion){
        alertDialogBuilder = new AlertDialog.Builder(this);
        final View vistaLugar = getLayoutInflater().inflate(R.layout.vista_eliminar_lugar, null);

        TextView tituloLugar = vistaLugar.findViewById(R.id.tituloLugar);
        tituloLugar.setText(nombre);

        Button elimina = vistaLugar.findViewById(R.id.eliminarLugar);
        Button cancelar = vistaLugar.findViewById(R.id.cancelar);
        elimina.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                places.remove(posicion);
                mostrarDatos(places);
                Toast.makeText(CreateRouteMap.this, getString(R.string.lugar_eliminado), Toast.LENGTH_LONG).show();
                alertDialog.dismiss();
            }
        });
        cancelar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
            }
        });

        alertDialogBuilder.setView(vistaLugar);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    //Elegir inicio y optimización de ruta
    private void configuraRuta(){
        alertDialogBuilder = new AlertDialog.Builder(this);
        final View vistaConfigura = getLayoutInflater().inflate(R.layout.vista_configura_ruta, null);

        final Boolean[] optionsChecked = new Boolean[2];

        final RadioGroup comenzarGroup = vistaConfigura.findViewById(R.id.comenzarDesdeGroup);
        final RadioGroup optimizarGroup = vistaConfigura.findViewById(R.id.optimizarGroup);
        Button aceptarRuta = vistaConfigura.findViewById(R.id.aceptarRuta);
        Button cancelarRuta = vistaConfigura.findViewById(R.id.cancelarRuta);

        aceptarRuta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int inicioDeRuta = comenzarGroup.getCheckedRadioButtonId();
                int optimizarRuta = optimizarGroup.getCheckedRadioButtonId();
                if(inicioDeRuta != -1 && optimizarRuta != -1){
                    Boolean[] configuracion = Functions.checkConfiguracion(inicioDeRuta, optimizarRuta);
                    optionsChecked[0] = configuracion[0];
                    optionsChecked[1] = configuracion[1];
                    String routeUrl = URLConstructor.getUrlRoute(
                            optionsChecked, miPosicionOriginal, Functions.getPlacesIDs(places),
                            getString(R.string.GOOGLE_MAPS_API_KEY), getString(R.string.idioma));
                    alertDialog.dismiss();
                    loadDisplayRoute(routeUrl, configuracion[0]);
                }else{
                    Toast.makeText(getApplicationContext(), getString(R.string.error_configure), Toast.LENGTH_LONG).show();
                }
            }
        });

        cancelarRuta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
            }
        });

        alertDialogBuilder.setView(vistaConfigura);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void loadDisplayRoute(String routeUrl, Boolean currentPos){
        Intent creaRuta = new Intent(CreateRouteMap.this, DisplayRoute.class);
        Bundle bundle = new Bundle();
        if(!fromFragment){
            bundle.putString("id", getIntent().getExtras().getString("id"));
        }
        bundle.putString("routeUrl", routeUrl);
        bundle.putBoolean("currentPos", currentPos);
        bundle.putParcelableArrayList("places", places);
        bundle.putBoolean("flag", true);
        creaRuta.putExtras(bundle);
        startActivity(creaRuta);
    }

    //Renderiza lista con lugares agregados a ruta
    private void mostrarDatos(final List<Lugar> places){
        PlaceAdapter adaptador = new PlaceAdapter(places, new ItemListener() {
            @Override
            public void itemOnClick(int posicion) {
                Lugar lugar = places.get(posicion);
                confirmaEliminarLugar(lugar.getTitulo(), posicion);
            }
        });
        vista.setLayoutManager(new LinearLayoutManager(CreateRouteMap.this));
        vista.setAdapter(adaptador);
    }

    private boolean checkFiltros(int pos, String radio){
        boolean res = true;
        if(pos == 0 || radio.isEmpty() || radio.equals(".")){
            res = false;
            Toast.makeText(getApplicationContext(), getString(R.string.error_parametros_req), Toast.LENGTH_LONG).show();
        }else{
            BigDecimal radioBusqueda = new BigDecimal(radio);
            int parteEntera = radioBusqueda.intValue();
            if(parteEntera<1){
                res = false;
                Toast.makeText(getApplicationContext(), getString(R.string.error_radio_min),Toast.LENGTH_LONG).show();
            }else if(parteEntera>5){
                res = false;
                Toast.makeText(getApplicationContext(), getString(R.string.error_radio_max),Toast.LENGTH_LONG).show();
            }else{
                tipoCadena = tipos[pos];
                radioNumero = radioBusqueda.doubleValue();
            }
        }
        return res;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.buscarButton:
                if(checkFiltros(tipoLugar.getSelectedItemPosition(), radioBusqueda.getText().toString())){
                    busca(tipoCadena, radioNumero);
                }
                break;
            case R.id.createRoute:
                if(places.size()>=2){
                    configuraRuta();
                }else{
                    Toast.makeText(getApplicationContext(), getString(R.string.error_2_lugares),Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onCameraMoveStarted(int newReason) {
        reason = newReason;
    }

    @Override
    public void onCameraIdle() {
        if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
            miPosicionDeCamaraCambiante = myMap.getCameraPosition().target;
            if(tipoCadena!=null && radioNumero!=null){
                busca(tipoCadena, radioNumero);
            }
        }
    }
}
