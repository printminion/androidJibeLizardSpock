package com.kupriyanov.jibe.lizardspock;

import android.app.Application;
import jibe.sdk.client.apptoapp.Config;

public class LizardSpockApplication extends Application {

	public static final String APP_ID = "cb87711fe3dc4f5c8fbd5eb84c2dc8b6";
	private static final String APP_SECRET = "964e3bb4e0354da88317c4ef6f0ff432";

	@Override
	public void onCreate() {
		super.onCreate();
		// set up App-ID and App-Secret in one central place.
		Config.getInstance().setAppToAppIdentifier(APP_ID, APP_SECRET);
	}

}