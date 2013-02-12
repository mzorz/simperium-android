package com.simperium;

import com.loopj.android.http.*;
import com.loopj.android.http.JsonHttpResponseHandler;

import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;

import com.simperium.BucketObject;

public class Simperium implements User.AuthenticationListener {

    public static final String HTTP_USER_AGENT = "android-1.0";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String USER_ACCESS_TOKEN_PREFERENCE = "user-access-token";
    private String appId;
    private String appSecret;

    public static final String TAG = "Simperium";
    protected AsyncHttpClient httpClient;
    protected AuthHttpClient authClient;
    protected WebSocketManager socketManager;
    
    private User user;
    private Context context;
    private User.AuthenticationListener authenticationListener;
    
    public Simperium(String appId, String appSecret, Context context){
        this(appId, appSecret, context, null);
    }
    
    public Simperium(String appId, String appSecret, Context context, User.AuthenticationListener authenticationListener){
        this.appId = appId;
        this.appSecret = appSecret;
        this.context = context;
        httpClient = new AsyncHttpClient();
        httpClient.setUserAgent(HTTP_USER_AGENT);
        authClient = new AuthHttpClient(appId, appSecret, httpClient);
        socketManager = new WebSocketManager(appId);
        this.authenticationListener = authenticationListener;
        loadUser();
    }
    
    private void loadUser(){
        user = new User(this);
        String token = getUserAccessToken();
        if (token != null) {
            user.setAccessToken(token);
            user.setAuthenticationStatus(User.AuthenticationStatus.AUTHENTICATED);
        } else {
            user.setAuthenticationStatus(User.AuthenticationStatus.NOT_AUTHENTICATED);
        }
    }
    
    public String getAppId(){
        return appId;
    }
    
    public User getUser(){
        return user;
    }
    
    public boolean needsAuthentication(){
        // we don't have an access token yet
        return user.needsAuthentication();
    }
    
    /**
     * Creates a bucket and starts syncing data and uses the provided
     * Class for to instantiate data
     *
     * @param bucketName the namespace to store the data in simperium
     * @param bucketType the Bucket.Diffable Class to use when deserializing data
     */
    public Bucket bucket(String bucketName, Class<? extends Bucket.Diffable>bucketType){
        // TODO: cache the bucket by user and bucketName and return the
        // same bucket if asked for again
        Bucket bucket = new Bucket(bucketName, bucketType, user);
        Channel channel = socketManager.createChannel(bucket, user);
        bucket.setChannel(channel);
        return bucket;
    }
    /**
     * Creates a bucket and starts syncing data. Users the generic BucketObject for
     * serializing and deserializing data
     *
     * @param bucketName namespace to store the data in simperium
     */
    public Bucket bucket(String bucketName){
        return bucket(bucketName, BucketObject.class);
    }
        
    public User createUser(String email, String password, User.AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.createUser(user, handler);
    }
    
    public User authorizeUser(String email, String password, User.AuthResponseHandler handler){
        user.setCredentials(email, password);
        return authClient.authorizeUser(user, handler);
    }
    
    public static final void log(String msg){
        Log.i(TAG, msg);
    }
    
    public void onAuthenticationStatusChange(User.AuthenticationStatus status){

        switch (status) {
            case AUTHENTICATED:
            // Start up the websocket
            // save the key
            saveUserAccessToken();
            socketManager.connect();
            break;
            case NOT_AUTHENTICATED:
            clearUserAccessToken();
            // Disconnect the websocket
            socketManager.disconnect();
            break;
            case UNKNOWN:
            // we haven't tried to auth yet or the socket is disconnected
            break;
        }
        if (authenticationListener != null) {
            authenticationListener.onAuthenticationStatusChange(status);
        }
    }
    
    private SharedPreferences.Editor getPreferenceEditor(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.edit();
    }
    
    private boolean clearUserAccessToken(){
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.remove(USER_ACCESS_TOKEN_PREFERENCE);
        return editor.commit();
    }
    
    private boolean saveUserAccessToken(){
        String token = user.getAccessToken();
        SharedPreferences.Editor editor = getPreferenceEditor();
        editor.putString(USER_ACCESS_TOKEN_PREFERENCE, token);
        return editor.commit();
    }
    
    private String getUserAccessToken(){
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String token = preferences.getString(USER_ACCESS_TOKEN_PREFERENCE, null);
        return token;
    }
    
                
}
