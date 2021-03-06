package com.internship.supercoders.superchat.authorization;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;

import com.internship.supercoders.superchat.api.ApiClient;
import com.internship.supercoders.superchat.api.ApiConstant;
import com.internship.supercoders.superchat.db.DBMethods;
import com.internship.supercoders.superchat.models.authorization_response.Session;
import com.internship.supercoders.superchat.models.user_authorization_response.ALog;
import com.internship.supercoders.superchat.models.user_authorization_response.VerificationData;
import com.internship.supercoders.superchat.models.user_update_request.UpdateUser;
import com.internship.supercoders.superchat.models.user_update_request.UpdateUserData;
import com.internship.supercoders.superchat.points.Points;
import com.internship.supercoders.superchat.utils.HmacSha1Signature;
import com.jakewharton.rxbinding.widget.RxTextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;


public class AuthInteractorImpl implements AuthInteractor {

    AuthView authView;
    private String signature;
    CompositeSubscription compositeSubscription;
    Matcher matcher;
    private Pattern pattern = android.util.Patterns.EMAIL_ADDRESS;
    AuthInteractorImpl() {

        compositeSubscription = new CompositeSubscription();
    }


    @Override
    public void writeUserAuthDataToDB(VerificationData verificationData) {
        // TODO: 1/30/17 [Code Review] NNNNNOOOOOOOOOOOOOO!!!!!!!!!!!!!!!!!!!
        DBMethods db = new DBMethods(authView.getContext());

        db.readFromDB();
        db.writeAuthData(verificationData);
    }


    @Override
    public boolean isAuthDataValid(String password, String email) {
        boolean isPasswordValid = validatePassword(password);
        boolean isEmailValid = validateEmail(email);
        return isEmailValid && isPasswordValid;
    }


    @Override
    public void userAthorization(DBMethods db, String password, String email, AuthFinishedListener listener) {
        String signatureParams = String.format("application_id=%s&auth_key=%s&nonce=%s&timestamp=%s&user[email]=%s&user[password]=%s",
                ApiConstant.APPLICATION_ID, ApiConstant.AUTH_KEY, ApiConstant.RANDOM_ID, ApiConstant.TS, email, password);
        try {
            signature = HmacSha1Signature.calculateRFC2104HMAC(signatureParams, ApiConstant.AUTH_SECRET);


        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        final Points.UserAuthorizatoinPoint apiUserAuth = ApiClient.getRetrofit().create(Points.UserAuthorizatoinPoint.class);
        Call<Session> call = apiUserAuth.userAuthorizatoin(new ALog(ApiConstant.APPLICATION_ID, ApiConstant.AUTH_KEY, ApiConstant.TS, Integer.toString(ApiConstant.RANDOM_ID), signature, new VerificationData(email, password)));
        call.enqueue(new Callback<Session>() {
            @Override
            public void onResponse(Call<Session> call, Response<Session> response) {
                if (response.isSuccessful()) {
                    Session session = response.body();
                    String token = session.getData().getToken();
                    Log.d("stas", token);
                    signIn(db, token, email, password, listener);
                } else {
                    try {
                        JSONObject jObjError = new JSONObject(response.errorBody().string());
                        Log.d("stas", "userAuthorization error = " + jObjError.getString("errors"));
                        listener.onError(jObjError.getString("errors"));
                    } catch (Exception e) {
                        Log.d("stas", e.getMessage());
                    }

                }


            }

            @Override
            public void onFailure(Call<Session> call, Throwable t) {

            }
        });

    }

    @Override
    public void validateUserInfo(EditText email, EditText password, AuthFinishedListener listener) {
        Observable<CharSequence> emailChangeObservable = RxTextView.textChanges(email);
        Observable<CharSequence> passwordChangeObservable = RxTextView.textChanges(password);

        Subscription emailSubscription = emailChangeObservable
                .doOnNext(next -> listener.hideError(1))
                .debounce(400, TimeUnit.MILLISECONDS)
                .filter(charSequence -> !TextUtils.isEmpty(charSequence))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(charSequence -> {

                    boolean isEmailValid = validateEmail(charSequence.toString());
                    if (!isEmailValid) {
                        listener.showEmailError();
                    } else {
                        listener.hideError(1);
                    }

                });


        Subscription passwordSubscrioption = passwordChangeObservable.doOnNext(next -> listener.hideError(2))
                .debounce(400, TimeUnit.MILLISECONDS)
                .filter(charSequence -> !TextUtils.isEmpty(charSequence))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(charSequence -> {
                    boolean isPasswordValid = validatePassword(charSequence.toString());
                    if (charSequence.toString().length() < 8) {
                        listener.showPasswordLengthError();
                    } else if (!isPasswordValid) {
                        listener.showPasswordError();
                    } else {
                        listener.hideError(2);
                    }


                });


        Subscription signInFieldsSubscription = Observable.combineLatest(emailChangeObservable, passwordChangeObservable,  (em, pas) -> {
            boolean isEmailValid = validateEmail(em.toString());
            boolean isPasswordLengthValid = pas.toString().length() >= 8;
            boolean isPasswordValid = validatePassword(pas.toString());


            return isEmailValid && isPasswordLengthValid && isPasswordValid ;
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(validFields -> {

                    if (validFields) {
                        listener.enableLogin();
                    } else {
                        listener.disableLogin();
                    }


                });




        compositeSubscription.add(passwordSubscrioption);
        compositeSubscription.add(emailSubscription);
        compositeSubscription.add(signInFieldsSubscription);


    }


    @Override
    public boolean validatePassword(String password) {
        if (TextUtils.isEmpty(password))
            return false;
        final Pattern pattern = Pattern.compile("^(?=.{8,12}$)(?=(.*[A-Z]){2})(?=(.*[a-z]){0,})(?=(.*[0-9]){2})(?=\\S+$).*$");
        matcher = pattern.matcher(password);
        return matcher.matches();
    }


    @Override
    public boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email))
            return false;
        matcher = pattern.matcher(email);
        return matcher.matches();
    }

    @Override
    public void unsubscribe() {
        compositeSubscription.unsubscribe();
    }


    @Override
    public void signIn(DBMethods db, String token, String login, String password, AuthFinishedListener listener) {
        Log.d("stas", login + "email");
        Log.d("stas", password + "password");
        final Points.SignInPoint2 apiSignIn = ApiClient.getRetrofit().create(Points.SignInPoint2.class);
        Call<UpdateUser> call = apiSignIn.signIn("application/json", "0.1.0", token, new VerificationData(login, password));
        call.enqueue(new Callback<UpdateUser>() {
            @Override
            public void onResponse(Call<UpdateUser> call, Response<UpdateUser> response) {
                if (response.isSuccessful()) {
                    UpdateUser user = response.body();
                    UpdateUserData userData = user.getUpdateUserData();
                    String blobId = userData.getBlob_id();
                    Log.d("stas", blobId + " = blobId");
                    String id = userData.getId();
                    Log.d("stas", id + " = user id");
                    String fullName = userData.getFull_name();
                    Log.d("stas", fullName + " = fullName");
                    String phone = userData.getPhone();
                    String website = userData.getWebsite();
                    String facebookId = userData.getFacebook_id();

                    db.saveMyInfo(null, blobId, Integer.parseInt(id), login, password, fullName, phone, website, facebookId);
                    db.writeToken(token);
                    if (blobId != null) {
                        Log.d("stas", "download");

                        downloadUserPhoto(db, id, blobId, token, listener);
                    } else {
                        listener.onSuccess(token);
                    }

                } else {
                    try {
                        JSONObject jObjError = new JSONObject(response.errorBody().string());
                        Log.d("stas", "signIn error = " + jObjError.getString("errors"));
                        listener.onError("" + jObjError.getString("errors"));
                    } catch (Exception e) {
                        Log.d("stas", e.getMessage());
                    }

                }

            }

            @Override
            public void onFailure(Call<UpdateUser> call, Throwable t) {
                Log.d("stas", t.getMessage());
            }
        });
    }

    @Override
    public void downloadUserPhoto(DBMethods db, String userId, String blobId, String token, AuthFinishedListener listener) {
        final Points.DownloadFilePoint downloadFilePoint = ApiClient.getRetrofit().create(Points.DownloadFilePoint.class);
        Call<ResponseBody> call = downloadFilePoint.downloadFile(blobId, "0.1.0", token);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    String name = randomName();
                    boolean writtenToDisk = writeResponseBodyToDisk(response.body(), name);

                    Log.d("stas", "file download was a success? " + writtenToDisk);

                    db.saveImagePath(name, userId);
                    listener.onSuccess(token);


                } else {
                    try {
                        JSONObject jObjError = new JSONObject(response.errorBody().string());
                        Log.d("stas", "downloadUserPhoto error = " + jObjError.getString("errors"));
                        listener.onError("" + jObjError.getString("errors"));
                    } catch (Exception e) {
                        Log.d("stas", e.getMessage());
                    }

                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("stas", t.getMessage());
            }
        });


    }


    private boolean writeResponseBodyToDisk(ResponseBody body, String name) {
        try {
            // todo change the file location/name according to your needs
            String root = Environment.getExternalStorageDirectory().toString();
            File dir = new File(root + "/SuperChat/ava/");
            if (!dir.exists()) dir.mkdirs();
            File fTo = new File(root + "/SuperChat/ava/" + name);
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                byte[] fileReader = new byte[4096];

                long fileSize = body.contentLength();
                long fileSizeDownloaded = 0;

                inputStream = body.byteStream();
                outputStream = new FileOutputStream(fTo);

                while (true) {
                    int read = inputStream.read(fileReader);

                    if (read == -1) {
                        break;
                    }

                    outputStream.write(fileReader, 0, read);

                    fileSizeDownloaded += read;

                    Log.d("stas", "file download: " + fileSizeDownloaded + " of " + fileSize);
                }

                outputStream.flush();

                return true;
            } catch (IOException e) {
                return false;
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }

                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (IOException e) {
            return false;
        }
    }


    public String randomName() {
        Random r = new Random();
        String alphabet = "abcdefghijklmnopqrstuvwxyz";

        final int N = 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        String randomName = sb.toString();
        return randomName + ".jpeg";
    }
}
