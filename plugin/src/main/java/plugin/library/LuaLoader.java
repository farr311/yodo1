//
//  LuaLoader.java
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

// This corresponds to the name of the Lua library,
// e.g. [Lua] require "plugin.library"
package plugin.library;

import android.app.Activity;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;

import com.yodo1.advert.callback.BannerCallback;
import com.yodo1.advert.callback.InterstitialCallback;
import com.yodo1.advert.callback.VideoCallback;
import com.yodo1.advert.entity.AdErrorCode;
import com.yodo1.advert.open.Yodo1Advert;


/**
 * Implements the Lua interface for a Corona plugin.
 * <p>
 * Only one instance of this class will be created by Corona for the lifetime of the application.
 * This instance will be re-used for every new Corona activity that gets created.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class LuaLoader implements JavaFunction, CoronaRuntimeListener {
	/** Lua registry ID to the Lua function to be called when the ad request finishes. */
	private int fListener;

	/** This corresponds to the event name, e.g. [Lua] event.name */
	private static final String EVENT_NAME = "pluginlibraryevent";
	private static String SDKCode = null;


	/**
	 * Creates a new Lua interface to this plugin.
	 * <p>
	 * Note that a new LuaLoader instance will not be created for every CoronaActivity instance.
	 * That is, only one instance of this class will be created for the lifetime of the application process.
	 * This gives a plugin the option to do operations in the background while the CoronaActivity is destroyed.
	 */
	@SuppressWarnings("unused")
	public LuaLoader() {
		// Initialize member variables.
		fListener = CoronaLua.REFNIL;

		// Set up this plugin to listen for Corona runtime events to be received by methods
		// onLoaded(), onStarted(), onSuspended(), onResumed(), and onExiting().
		CoronaEnvironment.addRuntimeListener(this);
	}

	/**
	 * Called when this plugin is being loaded via the Lua require() function.
	 * <p>
	 * Note that this method will be called every time a new CoronaActivity has been launched.
	 * This means that you'll need to re-initialize this plugin here.
	 * <p>
	 * Warning! This method is not called on the main UI thread.
	 * @param L Reference to the Lua state that the require() function was called from.
	 * @return Returns the number of values that the require() function will return.
	 *         <p>
	 *         Expected to return 1, the library that the require() function is loading.
	 */
	@Override
	public int invoke(LuaState L) {
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
			new InitWrapper(),
			new ShowBannerWrapper(),
			new HideBannerWrapper(),
			new ShowInterstitialWrapper(),
			new ShowRewardedVideo(),
		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	/**
	 * Called after the Corona runtime has been created and just before executing the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been loaded/initialized.
	 *                Provides a LuaState object that allows the application to extend the Lua API.
	 */
	@Override
	public void onLoaded(CoronaRuntime runtime) {}

	/**
	 * Called just after the Corona runtime has executed the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been started.
	 */
	@Override
	public void onStarted(CoronaRuntime runtime) {}

	/**
	 * Called just after the Corona runtime has been suspended which pauses all rendering, audio, timers,
	 * and other Corona related operations. This can happen when another Android activity (ie: window) has
	 * been displayed, when the screen has been powered off, or when the screen lock is shown.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been suspended.
	 */
	@Override
	public void onSuspended(CoronaRuntime runtime) {}

	/**
	 * Called just after the Corona runtime has been resumed after a suspend.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been resumed.
	 */
	@Override
	public void onResumed(CoronaRuntime runtime) {}

	/**
	 * Called just before the Corona runtime terminates.
	 * <p>
	 * This happens when the Corona activity is being destroyed which happens when the user presses the Back button
	 * on the activity, when the native.requestExit() method is called in Lua, or when the activity's finish()
	 * method is called. This does not mean that the application is exiting.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that is being terminated.
	 */
	@Override
	public void onExiting(CoronaRuntime runtime) {
		// Remove the Lua listener reference.
		CoronaLua.deleteRef( runtime.getLuaState(), fListener );
		fListener = CoronaLua.REFNIL;
	}

	/**
	 * Simple example on how to dispatch events to Lua. Note that events are dispatched with
	 * Runtime dispatcher. It ensures that Lua is accessed on it's thread to avoid race conditions
	 * @param message simple string to sent to Lua in 'message' field.
	 */
	@SuppressWarnings("unused")
	public void dispatchEvent(final String message) {
		CoronaEnvironment.getCoronaActivity().getRuntimeTaskDispatcher().send( new CoronaRuntimeTask() {
			@Override
			public void executeUsing(CoronaRuntime runtime) {
				LuaState L = runtime.getLuaState();

				CoronaLua.newEvent( L, EVENT_NAME );

				L.pushString(message);
				L.setField(-2, "message");

				try {
					CoronaLua.dispatchEvent( L, fListener, 0 );
				} catch (Exception ignored) {
				}
			}
		} );
	}

	/**
	 * The following Lua function has been called:  library.init( listener )
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param L Reference to the Lua state that the Lua function was called from.
	 * @return Returns the number of values to be returned by the library.init() function.
	 */
	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int init(LuaState L) {
		int listenerIndex = 1;

		// Fetch a reference to the Corona activity.
		// Note: Will be null if the end-user has just backed out of the activity.
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return 0;
		}

		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (SDKCode != null) {
					Yodo1Advert.initSDK(activity, SDKCode);
					dispatchEvent("Init successfully");

					if (CoronaLua.isListener(L, listenerIndex, EVENT_NAME)) {
						fListener = CoronaLua.newRef(L, listenerIndex);
					}
				}
			}
		} );

		return 0;
	}

	/**
	 * The following Lua function has been called:  library.show( word )
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param L Reference to the Lua state that the Lua function was called from.
	 * @return Returns the number of values to be returned by the library.show() function.
	 */
	@SuppressWarnings("WeakerAccess")
	public int showBanner(LuaState L) {
		// Fetch a reference to the Corona activity.
		// Note: Will be null if the end-user has just backed out of the activity.
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return 0;
		}

		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Yodo1Advert.showBanner(activity, new BannerCallback() {
					@Override
					public void onBannerClosed() {
						String message = "BannerCallback onBannerClosed";
						dispatchEvent(message);
					}

					@Override
					public void onBannerShow() {
						String message = "BannerCallback onBannerShow";
						dispatchEvent(message);
					}

					@Override
					public void onBannerShowFailed(AdErrorCode errorCode) {
						String message = "BannerCallback onBannerShowFailed, errorCode: " + errorCode;
						dispatchEvent(message);
					}

					@Override
					public void onBannerClicked() {
						String message = "BannerCallback onBannerClicked";
						dispatchEvent(message);
					}
				});
			}
		} );

		return 0;
	}

	@SuppressWarnings("WeakerAccess")
	private int hideBanner(LuaState L) {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return 0;
		}

		activity.runOnUiThread(new Runnable() {
		   public void run() {
			   Yodo1Advert.hideBanner(activity);
		   }
		} );

		return 0;
	}

    @SuppressWarnings("WeakerAccess")
    public int showInterstitial(LuaState L) {
        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            return 0;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean hasAds = Yodo1Advert.interstitialIsReady(activity);

                if (hasAds) {
                    Yodo1Advert.showInterstitial(activity, new InterstitialCallback() {

                        @Override
                        public void onInterstitialClosed() {
                            String message = "InterstitialCallback onInterstitialClosed";
                            dispatchEvent(message);
                        }

                        @Override
                        public void onInterstitialShowSucceeded() {
                            String message = "InterstitialCallback onInterstitialShowSucceeded";
                            dispatchEvent(message);
                        }

                        @Override
                        public void onInterstitialShowFailed(AdErrorCode adErrorCode) {
                            String message = "InterstitialCallback onInterstitialShowFailed, adErrorCode: "
                                    + adErrorCode;
                            dispatchEvent(message);
                        }

                        @Override
                        public void onInterstitialClicked() {
                            String message = "InterstitialCallback onInterstitialClicked";
                            dispatchEvent(message);
                        }
                    });
                } else {
                    String message = "ShowFailed";
                    dispatchEvent(message);
                }
            }
        } );

        return 0;
    }

    @SuppressWarnings("WeakerAccess")
    public int showRewardedVideo(LuaState L) {
        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            return 0;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean hasAds = Yodo1Advert.videoIsReady(activity);
                if (hasAds) {
                    Yodo1Advert.showVideo(activity, new VideoCallback() {

                        @Override
                        public void onVideoClosed(boolean isFinished) {
                            String message = "VideoCallback onVideoClosed, isFinished: " + isFinished;
                            dispatchEvent(message);
                        }

                        @Override
                        public void onVideoShow() {
                            String message = "VideoCallback onVideoShow";
                            dispatchEvent(message);
                        }

                        @Override
                        public void onVideoShowFailed(AdErrorCode errorCode) {
                            String message = "VideoCallback onVideoShowFailed, errorCode: " + errorCode;
                            dispatchEvent(message);
                        }

                        @Override
                        public void onVideoClicked() {
                            String message = "VideoCallback onVideoClicked";
                            dispatchEvent(message);
                        }
                    });
                } else {
                    String message = "ShowFailed";
                    dispatchEvent(message);
                }
            }
        } );

        return 0;
    }

	/** Implements the library.init() Lua function. */
	@SuppressWarnings("unused")
	private class InitWrapper implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "init";
		}
		
		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			return init(L);
		}
	}

	/** Implements the library.showBanner() Lua function. */
	@SuppressWarnings("unused")
	private class ShowBannerWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "showBanner";
		}

		@Override
		public int invoke(LuaState L) {
			return showBanner(L);
		}
	}

    /** Implements the library.showInterstitial() Lua function. */
    @SuppressWarnings("unused")
    private class ShowInterstitialWrapper implements NamedJavaFunction {
        @Override
        public String getName() {
            return "showInterstitial";
        }

        @Override
        public int invoke(LuaState L) {
            return showInterstitial(L);
        }
    }

    /** Implements the library.showRewardedVideo() Lua function. */
    @SuppressWarnings("unused")
    private class ShowRewardedVideo implements NamedJavaFunction {
        @Override
        public String getName() { return "showRewardedVideo"; }

        @Override
        public int invoke(LuaState L) {
            return showRewardedVideo(L);
        }
    }

	/** Implements the library.hideBanner() Lua function. */
	@SuppressWarnings("unused")
	private class HideBannerWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "hideBanner";
		}

		@Override
		public int invoke(LuaState L) {
			return hideBanner(L);
		}
	}
}
