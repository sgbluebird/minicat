package com.fanfou.app.hd;

import java.io.IOException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Selection;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.fanfou.app.hd.R;
import com.fanfou.app.hd.api.FanFouApi;
import com.fanfou.app.hd.api.ResultInfo;
import com.fanfou.app.hd.api.User;
import com.fanfou.app.hd.auth.FanFouOAuthProvider;
import com.fanfou.app.hd.auth.OAuthToken;
import com.fanfou.app.hd.auth.XAuthService;
import com.fanfou.app.hd.db.Contents.DirectMessageInfo;
import com.fanfou.app.hd.db.Contents.DraftInfo;
import com.fanfou.app.hd.db.Contents.StatusInfo;
import com.fanfou.app.hd.db.Contents.UserInfo;
import com.fanfou.app.hd.service.Constants;
import com.fanfou.app.hd.ui.widget.TextChangeListener;
import com.fanfou.app.hd.util.AlarmHelper;
import com.fanfou.app.hd.util.DeviceHelper;
import com.fanfou.app.hd.util.IntentHelper;
import com.fanfou.app.hd.util.OptionHelper;
import com.fanfou.app.hd.util.StringHelper;
import com.fanfou.app.hd.util.Utils;
import com.google.android.apps.analytics.GoogleAnalyticsTracker;

/**
 * @author mcxiaoke
 * @version 1.0 2011.06.10
 * @version 2.0 2011.10.17
 * @version 2.5 2011.10.25
 * @version 2.6 2011.10.26
 * @version 2.7 2011.10.27
 * @version 2.8 2011.11.26
 * @version 3.0 2011.12.01
 * @version 3.1 2011.12.06
 * @version 3.2 2011.12.13
 * @version 3.3 2011.12.14
 * 
 */
public final class UILogin extends Activity implements OnClickListener {

	private static final int REQUEST_CODE_REGISTER = 0;

	public static final String TAG = UILogin.class.getSimpleName();

	private UILogin mContext;
	private boolean destroyed;

	private GoogleAnalyticsTracker g;
	private int page;

	public void log(String message) {
		Log.i(TAG, message);
	}

	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";

	private EditText editUsername;
	private EditText editPassword;

	private Button mButtonSignin;

	private String username;
	private String password;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		init();
		setLayout();
	}

	private void init() {
		mContext = this;
		Utils.initScreenConfig(this);
		g = GoogleAnalyticsTracker.getInstance();
		g.startNewSession(getString(R.string.config_google_analytics_code),
				this);
		g.trackPageView("LoginPage");
	}

	private void setLayout() {
		setContentView(R.layout.login);

		editUsername = (EditText) findViewById(R.id.login_username);
		editUsername.addTextChangedListener(new TextChangeListener() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				username = s.toString();
			}
		});
		editPassword = (EditText) findViewById(R.id.login_password);
		editPassword.addTextChangedListener(new TextChangeListener() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				password = s.toString();
			}
		});
		editPassword.setOnEditorActionListener(new OnEditorActionListener() {

			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (App.DEBUG) {
					Log.d(TAG, "actionId=" + actionId + " KeyEvent=" + event);
				}
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					doLogin();
					return true;
				}
				return false;
			}
		});

		// mButtonRegister = (Button) findViewById(R.id.button_register);
		// mButtonRegister.setOnClickListener(this);

		mButtonSignin = (Button) findViewById(R.id.button_signin);
		mButtonSignin.setOnClickListener(this);

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		// case R.id.button_register:
		// goRegisterPage(mContext);
		// break;
		case R.id.button_signin:
			doLogin();
			break;
		default:
			break;
		}
	}

	private void doLogin() {
		if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
			Utils.notify(mContext, "密码和帐号不能为空");
		} else {
			Utils.hideKeyboard(this, editPassword);
			g.setCustomVar(1, "username", username);
			g.trackEvent("Action", "onClick", "Login", 1);
			new LoginTask().execute();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_REGISTER) {
			editUsername.setText(data.getStringExtra("email"));
			editPassword.setText(data.getStringExtra("password"));
			page = data.getIntExtra(Constants.EXTRA_PAGE, 0);
			new LoginTask().execute();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		App.active = true;
	}

	@Override
	protected void onPause() {
		App.active = false;
		super.onPause();
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		editUsername.setText(state.getString(USERNAME));
		Selection.setSelection(editUsername.getText(), editUsername.getText()
				.length());
		editPassword.setText(state.getString(PASSWORD));
		Selection.setSelection(editPassword.getText(), editPassword.getText()
				.length());
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(USERNAME, username);
		state.putString(PASSWORD, password);
	}

	private void clearData() {
		if (App.DEBUG)
			log("clearDB()");
		ContentResolver cr = getContentResolver();
		cr.delete(StatusInfo.CONTENT_URI, null, null);
		cr.delete(UserInfo.CONTENT_URI, null, null);
		cr.delete(DirectMessageInfo.CONTENT_URI, null, null);
		cr.delete(DraftInfo.CONTENT_URI, null, null);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		destroyed = true;
		if (g != null) {
			g.stopSession();
		}
	}

	private class LoginTask extends AsyncTask<Void, Integer, ResultInfo> {

		static final int LOGIN_IO_ERROR = 0; // 网络错误
		static final int LOGIN_AUTH_FAILED = 1; // 验证失败
		static final int LOGIN_AUTH_SUCCESS = 2; // 首次验证成功
		static final int LOGIN_CANCELLED_BY_USER = 3;

		private ProgressDialog progressDialog;
		private boolean isCancelled;

		@Override
		protected ResultInfo doInBackground(Void... params) {

			String savedUserId = OptionHelper.readString(mContext,
					R.string.option_userid, null);
			try {
				XAuthService xauth = new XAuthService(new FanFouOAuthProvider());
				OAuthToken token = xauth.getOAuthAccessToken(username,
						password);
				if (App.DEBUG)
					log("xauth token=" + token);

				if (isCancelled) {
					if (App.DEBUG) {
						log("login cancelled after xauth process.");
					}
					return new ResultInfo(LOGIN_CANCELLED_BY_USER,
							"user cancel login process.");
				}

				if (token != null) {
					publishProgress(1);
					App.setOAuthToken(token);
					User u = FanFouApi.newInstance().verifyAccount(Constants.MODE);

					if (isCancelled) {
						if (App.DEBUG) {
							log("login cancelled after verifyAccount process.");
						}
						return new ResultInfo(LOGIN_CANCELLED_BY_USER,
								"user cancel login process.");
					}

					if (u != null && !u.isNull()) {
						App.updateAccountInfo(mContext,u, token);
						if (App.DEBUG)
							log("xauth successful! ");

						if (StringHelper.isEmpty(savedUserId)
								|| !savedUserId.equals(u.id)) {
							clearData();
						}
						return new ResultInfo(LOGIN_AUTH_SUCCESS);
					} else {
						if (App.DEBUG)
							log("xauth failed.");
						return new ResultInfo(LOGIN_AUTH_FAILED,
								"XAuth successful, but verifyAccount failed. ");
					}
				} else {
					return new ResultInfo(LOGIN_AUTH_FAILED,
							"username or password is incorrect, XAuth failed.");
				}

			} catch (IOException e) {
				if (App.DEBUG) {
					e.printStackTrace();
				}
				return new ResultInfo(LOGIN_IO_ERROR,
						getString(R.string.msg_connection_error));
			}catch (Exception e) {
				if (App.DEBUG) {
					e.printStackTrace();
				}
				return new ResultInfo(LOGIN_IO_ERROR, e.getMessage());
			} finally {
			}
		}

		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(mContext);
			progressDialog.setMessage("正在进行登录认证...");
			progressDialog.setIndeterminate(true);
			progressDialog
					.setOnCancelListener(new DialogInterface.OnCancelListener() {

						@Override
						public void onCancel(DialogInterface dialog) {
							isCancelled = true;
							cancel(true);
						}
					});
			progressDialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			if (values.length > 0) {
				int value = values[0];
				if (value == 1) {
					progressDialog.setMessage("正在验证帐号信息...");
				}
			}
		}

		@Override
		protected void onPostExecute(ResultInfo result) {
			if (progressDialog != null && !destroyed) {
				progressDialog.dismiss();
			}
			switch (result.code) {
			case LOGIN_IO_ERROR:
			case LOGIN_AUTH_FAILED:
				Utils.notify(mContext, result.message);
				break;
			case LOGIN_CANCELLED_BY_USER:
				break;
			case LOGIN_AUTH_SUCCESS:
				if (g != null) {
					g.setCustomVar(2, "username", username);
					g.setCustomVar(2, "api", Build.VERSION.SDK);
					g.setCustomVar(2, "device", Build.MODEL);
					g.setCustomVar(2, "uuid", DeviceHelper.uuid(mContext));
					g.dispatch();
				}
				AlarmHelper.setScheduledTasks(mContext);
				IntentHelper.goHomePage(mContext, page);
				finish();
				break;
			default:
				break;
			}
		}

	}

}