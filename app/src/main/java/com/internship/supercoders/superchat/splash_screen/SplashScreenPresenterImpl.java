package com.internship.supercoders.superchat.splash_screen;

//import android.util.Log;

import android.util.Log;

import com.internship.supercoders.superchat.db.DBMethods;
import com.internship.supercoders.superchat.models.authorization_response.Session;
import com.internship.supercoders.superchat.models.user_authorization_response.VerificationData;
import com.internship.supercoders.superchat.models.user_update_request.UpdateUser;
import com.internship.supercoders.superchat.utils.UserPreferences;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class SplashScreenPresenterImpl implements SplashScreenPresenter {
    private SplashScreenView splashScreenView;
    private SplashScreenInteractor splashScreenInteractor;
    private volatile String token = null;
    // private boolean isAuthorize;
    private Subscription subscription = null;
    private Subscriber<Session> authSubscriber;

    SplashScreenPresenterImpl(SplashScreenView view, DBMethods dbManager, UserPreferences userPreferences) {
        this.splashScreenView = view;
        this.splashScreenInteractor = new SplashScreenInteractorImpl(dbManager, userPreferences);
        authSubscriber = new Subscriber<Session>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                e.getMessage();
                e.printStackTrace();
                //isAuthorize = false;
            }

            @Override
            public void onNext(Session session) {
                //Log.i(AppConsts.SPLASH_TAG, "Save token");
                splashScreenInteractor.saveToken(session.getData().getToken());
            }
        };
    }

    @Override
    public void unsubscribe() {
        splashScreenView = null;
        splashScreenInteractor = null;
        if (subscription != null)
            subscription.unsubscribe();

    }

    @Override
    public void sleep(final long milliseconds) {
        final boolean isAuthorize = splashScreenInteractor.isAuth();
        Log.d("stas", isAuthorize + " - isAuth");
        if (isAuthorize) {
            VerificationData user = splashScreenInteractor.getUserInfo();

            //Log.d("Splash", "Login: " + user.getEmail() + "Password: " + user.getPassword());
        /*    subscription = splashScreenInteractor.userAuthorization(user.getEmail(), user.getPassword())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(authSubscriber);*/


            Log.d("stas", user.getEmail() + " - email " + user.getPassword());
            splashScreenInteractor.userAuthorization(user.getEmail(), user.getPassword())
                    .map(session -> session.getData().getToken())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<String>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.d("stas", e.getMessage());

                        }

                        @Override
                        public void onNext(String s) {
                            splashScreenInteractor.saveToken(s);
                            Log.d("stas", "s = " + s);
                            splashScreenInteractor.signIn(s)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new Subscriber<UpdateUser>() {
                                        @Override
                                        public void onCompleted() {

                                        }

                                        @Override
                                        public void onError(Throwable e) {

                                        }

                                        @Override
                                        public void onNext(UpdateUser updateUser) {


                                        }
                                    });

                        }
                    });


        } else {
            /*subscription = splashScreenInteractor.createSession()
                    .subscribeOn(Schedulers.io())
                    .subscribe(authSubscriber);*/
        }

        Thread sleepThread = new Thread() {
            public void run() {
                try {
                    //Log.i(AppConsts.SPLASH_TAG, "Start");
                    Thread.sleep(milliseconds);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (isAuthorize) {
                    //Log.i(AppConsts.SPLASH_TAG, "ToMainScreen");
                    //Log.i(AppConsts.SPLASH_TAG, "token = " + splashScreenInteractor.getToken());
                    splashScreenView.navigateToMainScreen();
                } else {
                    //Log.i(AppConsts.SPLASH_TAG, "ToAuth");
                    //Log.i(AppConsts.SPLASH_TAG, "token = " + splashScreenInteractor.getToken());
                    splashScreenView.navigateToAuthorScreen();
                }
                //Log.i(AppConsts.SPLASH_TAG + " Presenter", "Call finish");
                splashScreenView.finish();
            }
        };
        sleepThread.start();
    }
}
