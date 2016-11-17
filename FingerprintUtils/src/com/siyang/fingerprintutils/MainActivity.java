package com.siyang.fingerprintutils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.fingerprints.FingerIdEntity;
import com.android.settings.fingerprints.FingerPrintManagerProxy;
import com.android.settings.fingerprints.FingerPrintUtil;
import com.android.settings.fingerprints.FingerprintRenameDialog;
import com.android.settings.fingerprints.IFingerPrintsBind;
import com.siyang.fingerprintutils.helper.ChooseLockSettingsHelper;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.service.fingerprint.FingerprintUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.siyang.fingerprintutils.R;

public class MainActivity extends Activity {

	private int fingerIdTemp;
	private ArrayList<FingerIdEntity> fingerPrintsList = new ArrayList();
	private ListView fingerprintList;
	FingerPrintManagerProxy fpm;
	private boolean isHide = false;
	boolean isNeedIdentify = false;
	boolean isNeedSwitchStateChanged = false;
	private boolean isPageFinished;
	private boolean isToRename;

	private LockPatternUtils mLockPatternUtils;
	Switch mobileUnlockSwitch;
	private View mobileUnlockView;
	private Switch mobileWakeupSwitch;
	private View mobileWakeupView;
	private Switch photoSwitch;
	FingerprintRenameDialog renameDialog;
	AlertDialog tipsDialog;
	private TextView unlockFingerDesc;
	private FingerAdapter mFingerAdapter;

	boolean isAdd = false;
	boolean isRemove = true;
	Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			if (isAdd) {
				if (!isRemove) {
					switch (msg.what) {
					case 1:
						MainActivity.this.setItemChecked(msg.arg1);
						break;
					case 2:
						Log.i("ManagerFingerprintsFragment",
								"REGESTER_IDENTIFY");
						if (MainActivity.this.isResumed()) {
							MainActivity.this.regesterIdentifyListener();
							return;
						}
						Log.i("ManagerFingerprintsFragment",
								"not resumed ; can not identify");
						break;
					case 3:
						Toast.makeText(MainActivity.this,
								R.string.fingerprints_bind_failed,
								Toast.LENGTH_SHORT).show();
						break;

					case 4:
						MainActivity.this.refreshFingerPrint();
						return;
					case 5:
						MainActivity.this.removeFingPrintId(msg.arg1);
						return;
					case 6:
						if (MainActivity.this.isResumed()) {
							MainActivity.this.bindAndRegesterIdentifyListener();
						}
						break;
					case 7:
						FingerPrintUtil.removeAllFingers(
								getFingerPrintManagerProxy(), fingerPrintsList);
						mFingerAdapter.notifyDataSetChanged();
						refreshDataAndUI();
						break;
					default:
						break;
					}
				}
			} else {
				Log.i("ManagerFingerprintsFragment", "do not deal message ");
			}
		}
	};

	BroadcastReceiver keyguardReceive = new BroadcastReceiver() {
		public void onReceive(Context paramAnonymousContext,
				Intent paramAnonymousIntent) {
			String action = paramAnonymousIntent.getAction();
			Log.i("FingerprintsFragment", "KeyGuardRecerive action " + action);
			if ("android.intent.action.SCREEN_OFF".equals(action)) {
				MainActivity.this.hidePage();
			}
		}
	};

	@Override
	protected void onStart() {
		super.onStart();
		isAdd = true;
		isRemove = false;
		secureVerify(true);

	}

	@Override
	protected void onStop() {
		super.onStop();
		isAdd = false;
		isRemove = true;
	}

	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(this.keyguardReceive);
	}

	public void onPause() {
		super.onPause();
		Log.i("ManagerFingerprintsFragment", "onPause");
		this.mHandler.removeMessages(6);
		this.mHandler.removeMessages(2);
		FingerPrintUtil.abortIdentify(getFingerPrintManagerProxy());
		FingerPrintUtil.unbindService(getFingerPrintManagerProxy());
	}

	public void onResume() {
		super.onResume();
		Log.i("ManagerFingerprintsFragment", "onResume");
		if (this.isNeedIdentify) {
			this.mHandler.sendEmptyMessageDelayed(6, 200L);
			return;
		}
		this.isNeedIdentify = true;
	}

	private void secureVerify(boolean paramBoolean) {
		if (this.mLockPatternUtils == null) {
			this.mLockPatternUtils = new LockPatternUtils(this);
		}
		Log.i("ManagerFingerprintsFragment", "hasConfirmed  " + paramBoolean);
		ChooseLockSettingsHelper localChooseLockSettingsHelper = new ChooseLockSettingsHelper(
				this);
		if ((!paramBoolean)
				&& (FingerPrintUtil.isUnlockSwitchOn(this))
				&& (localChooseLockSettingsHelper.launchConfirmationActivity(
						100, null, null, true))) {
			this.isHide = true;
			return;
		}
		this.isHide = false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction("android.intent.action.SCREEN_OFF");
		registerReceiver(this.keyguardReceive, iFilter);
		mobileUnlockView = findViewById(R.id.unlock_switch_view);
		mobileUnlockSwitch = (Switch) findViewById(R.id.unlock_switch);
		unlockFingerDesc = (TextView) findViewById(R.id.unlock_finger_desc);

		mobileWakeupView = findViewById(R.id.wakeup_switch_view);
		mobileWakeupSwitch = (Switch) findViewById(R.id.wakeup_switch);
		mobileWakeupView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				mobileWakeupSwitch.setChecked(!mobileWakeupSwitch.isChecked());
			}
		});
		mobileWakeupSwitch.setChecked(isSupprotFingerPrintWakeup());

		mobileUnlockView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				onUnlockSwitchNeedChange();
			}
		});
		mobileWakeupSwitch
				.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged(
							CompoundButton paramAnonymousCompoundButton,
							boolean paramAnonymousBoolean) {
						Log.i("jiepp",
								"mobileWakeupSwitch onCheckedChanged arg1 "
										+ paramAnonymousBoolean);
						FingerPrintUtil.setSupprotFingerPrintWakeup(
								paramAnonymousBoolean, MainActivity.this);
					}
				});
		updateSwitchUI();
		fingerprintList = (ListView) findViewById(R.id.fingerprints_list);

		View view = LayoutInflater.from(this).inflate(
				R.layout.fingerprint_footer, null, false);

		fingerprintList.addFooterView(view);
		mFingerAdapter = new FingerAdapter(this, 0);
		fingerprintList.setAdapter(mFingerAdapter);
		resetHeight(FingerPrintUtil.getFingersFromDb(this).size());
		fingerprintList.setOnItemClickListener(new OnItemClickListener() {
			boolean quickClick = false;
			Runnable quickClickRun = new Runnable() {
				public void run() {
					quickClick = false;
				}
			};

			@Override
			public void onItemClick(AdapterView<?> adapterView, View view,
					int position, long id) {

				if (quickClick) {
					return;
				}
				quickClick = true;
				mHandler.removeCallbacks(this.quickClickRun);
				mHandler.postDelayed(this.quickClickRun, 200L);
				Log.i("ManagerFingerprintsFragment", "onItemClick " + position);
				int maxP = fingerPrintsList.size();
				if (position == maxP) {
					if (maxP == 0) {
						isNeedSwitchStateChanged = true;
						addFingerPrint(true);
						return;
					}
					if (mLockPatternUtils.isSecure()) {
						addFingerPrint(false);
						return;
					}
					showFactoryResetProtectionWarningDialog(true);
					return;
				}
				Log.i("ManagerFingerprintsFragment", "SHOW RENAME DIALOG");
				showItemClickDialog(fingerPrintsList.get(position).fingerId,
						position);
			}
		});
		BindAndRefreshUI();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.i("ManagerFingerprintsFragment", "requestCode " + requestCode
				+ " resultCode == " + resultCode);
		switch (requestCode) {
		case 100:
			if (resultCode == RESULT_OK) {
				isHide = false;
				BindAndRefreshUI();
			} else {
				if (isNeedSwitchStateChanged) {
					mobileUnlockSwitch.setChecked(false);
					isNeedSwitchStateChanged = false;
					BindAndRefreshUI();
				} else {
					onBackPressed();
				}
			}
			break;
		case 107:
			if (mLockPatternUtils == null) {
				mLockPatternUtils = new LockPatternUtils(this);
			}
			FingerPrintUtil.setUnlockSwitch(this, mLockPatternUtils.isSecure());
			mobileWakeupSwitch.setChecked(mLockPatternUtils.isSecure());
			updateSwitchUI();

			break;
		case 111:
			if (resultCode == RESULT_OK) {
				if (data != null) {
					isToRename = data.getBooleanExtra("isToRename", false);
				}
				if (isNeedSwitchStateChanged) {
					if (mLockPatternUtils.isSecure()) {
						new ChooseLockSettingsHelper(this)
								.launchConfirmationActivity(112, null, null);
					} else {
						launchChooseLockscreen(124, true);
					}
				} else {
					BindAndRefreshUI();
				}
			} else {
				if (isNeedSwitchStateChanged) {
					mobileUnlockSwitch.setChecked(false);
					isNeedSwitchStateChanged = false;
					BindAndRefreshUI();
				}
				this.fingerIdTemp = -1;
			}
			break;

		case 112:
			if (resultCode == RESULT_OK) {
				this.isHide = false;
				this.mobileWakeupSwitch.setChecked(true);
				BindAndRefreshUI();
			} else {
				removeTheLastFingerprint();
				if (this.isNeedSwitchStateChanged) {
					this.mobileUnlockSwitch.setChecked(false);
					this.isNeedSwitchStateChanged = false;
					BindAndRefreshUI();
				} else {
					onBackPressed();
				}
			}
			break;
		case 123:
			if (this.mLockPatternUtils.isSecure()) {
				this.mobileWakeupSwitch.setChecked(true);
				BindAndRefreshUI();
			} else {
				removeTheLastFingerprint();
				if (this.isNeedSwitchStateChanged) {
					this.mobileUnlockSwitch.setChecked(false);
					this.isNeedSwitchStateChanged = false;
				}
				this.fingerIdTemp = -1;
				BindAndRefreshUI();
			}
			break;
		case 124:
			BindAndRefreshUI();
			break;
		case 414:
			if (resultCode == RESULT_OK) {
				FingerPrintUtil.setUnlockSwitch(this, true);
				this.mobileUnlockSwitch.setChecked(true);
				this.mobileWakeupView.setVisibility(0);
				if (mobileWakeupSwitch.isChecked()) {
					FingerPrintUtil.setSupprotFingerPrintWakeup(true, this);
				}
			} else {
				updateSwitchUI();
			}
			break;
		}
	}

	private void BindAndRefreshUI() {
		this.isNeedIdentify = false;
		if (FingerPrintUtil.isBinded(getFingerPrintManagerProxy())) {
			this.mHandler.sendEmptyMessage(4);
			this.mHandler.sendEmptyMessage(2);
			return;
		}
		FingerPrintUtil.bindService(getFingerPrintManagerProxy(),
				new IFingerPrintsBind() {
					public void onBindedFail() {
						mHandler.sendEmptyMessage(3);
					}

					public void onBindedSuccess() {
						mHandler.sendEmptyMessage(4);
						mHandler.sendEmptyMessage(2);
					}
				});
	}

	private void updateSwitchUI() {
		if (FingerPrintUtil.isUnlockSwitchOn(this)) {
			this.mobileUnlockSwitch.setChecked(true);
			this.mobileWakeupView.setVisibility(0);
			FingerPrintUtil.setSupprotFingerPrintWakeup(
					this.mobileWakeupSwitch.isChecked(), this);
			return;
		}
		this.mobileUnlockSwitch.setChecked(false);
		this.mobileWakeupView.setVisibility(8);
	}

	private void removeTheLastFingerprint() {
		getFingerIds();
		int i = this.fingerPrintsList.size();
		if (i > 0) {
			removeFingPrintId(((FingerIdEntity) this.fingerPrintsList
					.get(i - 1)).fingerId);
		}
	}

	protected void showItemClickDialog(final int fingerId, final int position) {
		if ((this.tipsDialog != null) && (this.tipsDialog.isShowing())) {
			return;
		}
		FingerPrintUtil.abortIdentify(getFingerPrintManagerProxy());
		String str1 = getString(R.string.fingerprints_name);
		String str2 = getString(R.string.fingerprints_remove);
		AlertDialog.Builder localBuilder = new AlertDialog.Builder(this);
		DialogInterface.OnClickListener local10 = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface paramAnonymousDialogInterface,
					int paramAnonymousInt) {
				Log.i("ManagerFingerprintsFragment", "which "
						+ paramAnonymousInt);
				if (paramAnonymousInt == 0) {
					showRenameDialog(position);
					return;
				}
				bindAndRemoveId(fingerId, true);
			}
		};
		localBuilder.setItems(new CharSequence[] { str1, str2 }, local10);
		localBuilder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(
							DialogInterface paramAnonymousDialogInterface,
							int paramAnonymousInt) {
						Log.i("ManagerFingerprintsFragment",
								"setNegativeButton which " + paramAnonymousInt);
					}
				});
		localBuilder
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					public void onDismiss(
							DialogInterface paramAnonymousDialogInterface) {
						mHandler.sendEmptyMessageDelayed(6, 50L);
					}
				});
		this.tipsDialog = localBuilder.create();
		this.tipsDialog.show();
	}

	protected void showRenameDialog(int position) {
		/*
		 * if (((this.renameDialog != null) && (this.renameDialog.isShowing())))
		 * { return; } this.renameDialog = new
		 * FingerprintRenameDialog(getActivity(),
		 * ((FingerIdEntity)this.fingerPrintsList.get(paramInt)).fingerId,
		 * ((FingerIdEntity)this.fingerPrintsList.get(paramInt)).name,
		 * this.fingerPrintsList, new FingerprintRenameDialog.OnRenameListener()
		 * { public void onRename(String paramAnonymousString, int
		 * paramAnonymousInt) { if (!TextUtils.isEmpty(paramAnonymousString)) {
		 * Log.i("ManagerFingerprintsFragment", "newName " +
		 * paramAnonymousString);
		 * FingerPrintUtil.getInstance(ManagerFingerprintsFragment
		 * .this.getActivity()).setNewFingerName(paramAnonymousInt,
		 * paramAnonymousString, true);
		 * ((FingerIdEntity)ManagerFingerprintsFragment
		 * .this.fingerPrintsList.get(paramInt)).name = paramAnonymousString;
		 * ManagerFingerprintsFragment.this.setItemChecked(-1);
		 * ManagerFingerprintsFragment.this.bindAndRename(); } } });
		 * this.renameDialog.show();
		 */
	}

	private void onUnlockSwitchNeedChange() {
		boolean isChecked = !mobileUnlockSwitch.isChecked();

		Log.i("ManagerFingerprintsFragment", "mobileUnlockSwitch check "
				+ isChecked);
		if (isChecked) {
			toOpenUnlockSwitch();
		} else {
			this.mobileUnlockSwitch.setChecked(isChecked);
			FingerPrintUtil.setUnlockSwitch(this, false);
			this.mobileWakeupView.setVisibility(8);
		}

	}

	private void toOpenUnlockSwitch() {
		if ((this.fingerPrintsList == null)
				|| (this.fingerPrintsList.size() == 0)) {
			this.isNeedSwitchStateChanged = true;
			addFingerPrint(true);
			return;
		}
		if (this.mLockPatternUtils.isSecure()) {
			new ChooseLockSettingsHelper(this).launchConfirmationActivity(107,
					null, null);
			return;
		}
		showFactoryResetProtectionWarningDialog(false);
	}

	protected void bindAndRemoveId(int fingerId, final boolean b) {
		final Message localMessage = new Message();
		localMessage.what = 5;
		localMessage.arg1 = fingerId;
		if (FingerPrintUtil.isBinded(getFingerPrintManagerProxy())) {
			this.mHandler.sendMessage(localMessage);
			if (b) {
				this.mHandler.sendEmptyMessage(4);
				this.mHandler.sendEmptyMessage(2);
			}
			return;
		}
		FingerPrintUtil.bindService(getFingerPrintManagerProxy(),
				new IFingerPrintsBind() {
					public void onBindedFail() {
						mHandler.sendEmptyMessage(3);
					}

					public void onBindedSuccess() {
						mHandler.sendMessage(localMessage);
						if (b) {
							mHandler.sendEmptyMessage(4);
							mHandler.sendEmptyMessage(2);
						}
					}
				});
	}

	protected void showFactoryResetProtectionWarningDialog(boolean b) {
		FactoryResetProtectionWarningDialog localFactoryResetProtectionWarningDialog = new FactoryResetProtectionWarningDialog();
		Bundle localBundle = new Bundle();
		localBundle.putBoolean("fromAddItem", b);
		localFactoryResetProtectionWarningDialog.setArguments(localBundle);
		localFactoryResetProtectionWarningDialog.show(getFragmentManager(),
				"renote");
	}

	private void setItemChecked(int paramInt) {
		Iterator<FingerIdEntity> localIterator = this.fingerPrintsList
				.iterator();
		while (localIterator.hasNext()) {
			FingerIdEntity localFingerIdEntity = localIterator.next();
			if (localFingerIdEntity.fingerId == paramInt) {
				localFingerIdEntity.ischecked = true;
			} else {
				localFingerIdEntity.ischecked = false;
			}
		}
		Log.i("ManagerFingerprintsFragment", "setItemChecked " + paramInt
				+ " fingerPrintsList " + this.fingerPrintsList);
		this.mFingerAdapter.notifyDataSetChanged();
	}

	private void regesterIdentifyListener() {
		if (isPageFinished) {
			return;
		}
		if (!fingerPrintsList.isEmpty()) {
			FingerPrintUtil.startIdentify(getFingerPrintManagerProxy(),
					new FingerPrintManagerProxy.ISettingsIdentifyCallback() {
						public void onIdentified(int paramAnonymousInt) {
							Log.i("ManagerFingerprintsFragment",
									"onIdentified " + paramAnonymousInt);
							Message localMessage = new Message();
							localMessage.arg1 = paramAnonymousInt;
							localMessage.what = 1;
							mHandler.sendMessage(localMessage);
						}

						public void onIdentifyCaptureFailed(
								int paramAnonymousInt) {
							Log.i("ManagerFingerprintsFragment",
									"onIdentifyCaptureFailed");
							Message localMessage = new Message();
							localMessage.arg1 = -1;
							localMessage.what = 1;
							mHandler.sendMessage(localMessage);
						}

						public void onNoMatch() {
							Log.i("ManagerFingerprintsFragment", "onNoMatch");
							Message localMessage = new Message();
							localMessage.arg1 = -1;
							localMessage.what = 1;
							mHandler.sendMessage(localMessage);
						}
					});
		}

	}

	private void refreshFingerPrint() {
		getFingerIds();
		int preIndex = 1;
		Log.i("ManagerFingerprintsFragment", "PRE preFingerPrintIndex "
				+ preIndex);

		if (fingerprintList != null && fingerPrintsList.size() > 0) {
			FingerPrintUtil fingerprintUtils = FingerPrintUtil
					.getInstance(this);
			int length = fingerPrintsList.size();
			for (int j = 0; j < length; j++) {
				FingerIdEntity idEntity = fingerPrintsList.get(j);
				String fingerprintName = fingerprintUtils.getString(
						idEntity.fingerId + "", null);
				if (TextUtils.isEmpty(fingerprintName)) {
					Set<Integer> fingers = new HashSet<Integer>();
					boolean hasAllRename = true;
					Log.i("ManagerFingerprintsFragment",
							"refreshFingerPrint fingerPrintsList ");
					Iterator<FingerIdEntity> iterator = fingerPrintsList
							.iterator();
					while (iterator.hasNext()) {
						FingerIdEntity item = iterator.next();
						String name = item.name;
						if (!TextUtils.isEmpty(name)) {
							// continue;
							if (!item.isRenamed) {
								fingers.add(Integer.parseInt(item.name
										.replaceAll("[^0-9]", "")));
							} else if (hasAllRename) {
								hasAllRename = false;
								preIndex = length;
							}
						}
					}
					if (!hasAllRename) {
						Log.i("ManagerFingerprintsFragment", "hasAllRename="
								+ hasAllRename);
					} else {
						if (fingers.contains(Integer.valueOf(5))) {
							for (int i = 1; i <= 5; i++) {
								if (!(fingers.contains(Integer.valueOf(i)))) {
									Log.i("ManagerFingerprintsFragment",
											"fingers=" + fingers + " j = " + i);
									preIndex = i;
								}
							}
						} else {
							int max = 1;
							int tmp = 0;
							Iterator<Integer> it = fingers.iterator();
							while (it.hasNext()) {
								tmp = it.next();
								if (tmp > max) {
									max = tmp;
								}
							}
							max += 1;
							preIndex = max;
							Log.i("ManagerFingerprintsFragment",
									"========== max =======" + max);
						}
						fingerprintName = getString(R.string.fingerprint,
								new Object[] { Integer.valueOf(preIndex) });
						fingerprintUtils.setNewFingerName(idEntity.fingerId,
								fingerprintName, false);
					}

				} else {
					idEntity.name = fingerprintName;
				}
			}
			if (this.isToRename) {
				this.isToRename = false;
				if (this.fingerPrintsList.size() > 0) {
					showRenameDialog(this.fingerPrintsList.size() - 1);
				}
			}
			Log.i("ManagerFingerprintsFragment", "AFTER preFingerPrintIndex "
					+ preIndex);
			if (fingerPrintsList.isEmpty()) {
				resetSwtichUI(false);
			}
			if (isNeedSwitchStateChanged) {
				isNeedSwitchStateChanged = false;
				resetSwtichUI(true);
			}
			refreshDataAndUI();
		}

	}

	private void resetSwtichUI(boolean paramBoolean) {
		Log.i("ManagerFingerprintsFragment", "resetSwtichUI " + paramBoolean);
		FingerPrintUtil.setUnlockSwitch(this, paramBoolean);
		updateSwitchUI();
	}

	public static boolean isSupprotFingerPrintWakeup() {
		String str = SystemProperties.get("persist.sys.fingerprint.wakeup");
		Log.i("FingerPrintUtil", "str " + str);
		return "yes".equals(str);
	}

	public static boolean isUnlockSwitchOn(Context paramContext) {
		boolean bool = true;
		if (paramContext == null) {
			return false;
		}
		int i = Settings.System.getInt(paramContext.getContentResolver(),
				"nubia_fingerprint_unlock_switch", 0);
		bool = (i == 1);
		Log.i("jiepp", "switchInt" + i);

		return bool;
	}

	private void removeFingPrintId(int paramInt) {
		FingerPrintUtil.removeOneSecureFingerId(getFingerPrintManagerProxy(),
				paramInt);
	}

	private void refreshDataAndUI() {
		resetHeight(this.fingerPrintsList.size());
		LinearLayout.LayoutParams localLayoutParams = (LinearLayout.LayoutParams) this.fingerprintList
				.getLayoutParams();
		if (isAdd) {
			localLayoutParams.height = ((this.fingerPrintsList.size() + 1)
					* getResources().getDimensionPixelSize(
							R.dimen.finger_item_hight) + this.fingerPrintsList
					.size());
		}
		setItemChecked(-1);
	}

	private void resetHeight(int paramInt) {
		TypedArray localTypedArray = this.mobileUnlockView
				.getContext()
				.getTheme()
				.obtainStyledAttributes(
						new int[] { android.R.attr.listPreferredItemHeight,
								android.R.attr.listPreferredItemHeightSmall });
		float f1 = localTypedArray.getDimension(0, -1.0F);
		float f2 = localTypedArray.getDimension(1, -1.0F);
		if (paramInt > 0) {
			this.unlockFingerDesc.setVisibility(8);
			this.mobileUnlockView.setMinimumHeight((int) f2);
		} else {
			this.unlockFingerDesc.setVisibility(0);
			this.unlockFingerDesc.setText(R.string.fingerprint_enroll_first);
			this.mobileUnlockView.setMinimumHeight((int) f1);
		}

	}

	private void addFingerPrint(boolean paramBoolean) {
		if ((this.fingerPrintsList != null)
				&& (this.fingerPrintsList.size() >= 5)) {
			Toast.makeText(
					this,
					getString(R.string.fingerprint_maxnum,
							new Object[] { Integer.valueOf(5) }),
					Toast.LENGTH_SHORT).show();
			return;
		}
		toAddFingerprintPage(paramBoolean);
	}

	private void bindAndRegesterIdentifyListener() {
		if (FingerPrintUtil.isBinded(getFingerPrintManagerProxy())) {
			this.mHandler.sendEmptyMessage(2);
			return;
		}
		FingerPrintUtil.bindService(getFingerPrintManagerProxy(),
				new IFingerPrintsBind() {
					public void onBindedFail() {
						mHandler.sendEmptyMessage(3);
					}

					public void onBindedSuccess() {
						Log.i("ManagerFingerprintsFragment",
								"bindAndRegesterIdentifyListener onBindedSuccess");
						mHandler.sendEmptyMessage(2);
					}
				});
	}

	private void toAddFingerprintPage(boolean paramBoolean) {
		try {
			this.fingerIdTemp = getNewFingerId();
			Log.i("FingerprintsFragment", "fingerIdTemp " + this.fingerIdTemp);
			Intent localIntent = new Intent();
			localIntent.setClassName("cn.nubia.fingerprints",
					"cn.nubia.fingerprints.TutorialActivity");
			localIntent.putExtra("finger_id", this.fingerIdTemp);
			if (paramBoolean) {
				localIntent.putExtra("add_finger_for", "only");
			}
			startActivityForResult(localIntent, 111);
			return;
		} catch (Exception localException) {
			Log.e("FingerprintsFragment", "Exception", localException);
		}
	}

	private void getFingerIds() {
		this.fingerPrintsList = FingerPrintUtil
				.getAllSecureFingerIds(getFingerPrintManagerProxy());
	}

	private FingerPrintManagerProxy getFingerPrintManagerProxy() {
		if (this.fpm == null) {
			this.fpm = new FingerPrintManagerProxy(this);
		}
		return this.fpm;
	}

	private int getNewFingerId() {
		return FingerPrintUtil.getNewFingerId(this);
	}

	private void hidePage() {
		try {
			Object localObject = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
					.getRunningTasks(1);
			if (localObject != null) {
				localObject = ((ActivityManager.RunningTaskInfo) ((List) localObject)
						.get(0)).topActivity;
				String str1 = ((ComponentName) localObject).getPackageName();
				String str2 = ((ComponentName) localObject).getClassName();
				Log.i("FingerprintsFragment", "runningPack = " + str1
						+ " className " + str2);
				if (((ComponentName) localObject).equals(getComponentName())) {
					onBackPressed();
				}
			}
			return;
		} catch (Exception localException) {
			Log.i("FingerprintsFragment", "Exception", localException);
		}
	}

	private void launchChooseLockscreen(int paramInt, boolean paramBoolean) {
		Intent localIntent = new Intent("android.app.action.SET_NEW_PASSWORD");
		localIntent.putExtra("minimum_quality", 65536);
		localIntent.putExtra("isfrom_finger", true);
		localIntent.putExtra("fingerprint_unlock_method", paramBoolean);
		localIntent.putExtra("fingerprint_chooselock_title",
				getString(R.string.fingerprint_chooselock_title));
		startActivityForResult(localIntent, paramInt);
	}

	private void bindAndRemoveAll() {
		if ((this.fingerPrintsList == null)
				|| (this.fingerPrintsList.size() == 0)) {
			return;
		}
		if (FingerPrintUtil.isBinded(getFingerPrintManagerProxy())) {
			this.mHandler.sendEmptyMessage(7);
			return;
		}
		FingerPrintUtil.bindService(getFingerPrintManagerProxy(),
				new IFingerPrintsBind() {
					public void onBindedFail() {
						mHandler.sendEmptyMessage(3);
					}

					public void onBindedSuccess() {
						mHandler.sendEmptyMessage(7);
					}
				});
	}

	class FingerAdapter extends ArrayAdapter<FingerIdEntity> {

		private LayoutInflater mInflater;

		public FingerAdapter(Context context, int resource) {
			super(context, 0);
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			return fingerPrintsList.size();
		}

		@Override
		public FingerIdEntity getItem(int position) {
			return fingerPrintsList.get(position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder = null;

			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.fingerprint_footer,
						parent, false);
				holder = new ViewHolder();
				holder.fingerName = (TextView) convertView
						.findViewById(R.id.fingerprint_name);
				ImageView image = (ImageView) convertView
						.findViewById(R.id.finger_item_view);
				image.setAlpha(0.0f);
				image.setVisibility(View.VISIBLE);
				holder.oa = ObjectAnimator.ofFloat(image, "alpha", new float[] {
						0.0f, 1.0f, 0.0f });
				holder.oa.setDuration(1500L);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			holder.fingerName.setText(getItem(position).name);
			if ((getItem(position).ischecked) && (!holder.oa.isRunning())) {
				holder.oa.start();
			}
			return convertView;
		}
	}

	private static class ViewHolder {
		TextView fingerName;
		ObjectAnimator oa;
	}

	public class FactoryResetProtectionWarningDialog extends DialogFragment {
		public FactoryResetProtectionWarningDialog() {
		}

		public void onCancel(DialogInterface paramDialogInterface) {
			super.onCancel(paramDialogInterface);
			isNeedSwitchStateChanged = false;
		}

		public Dialog onCreateDialog(Bundle paramBundle) {
			final boolean bool = getArguments()
					.getBoolean("fromAddItem", false);
			return new AlertDialog.Builder(getActivity())
					.setMessage(R.string.fingerprint_set_screenlock_renote)
					.setPositiveButton(
							R.string.fingerprint_set_screenlock_renote_retain,
							new DialogInterface.OnClickListener() {
								public void onClick(
										DialogInterface paramAnonymousDialogInterface,
										int paramAnonymousInt) {
									if (bool) {
										addFingerPrint(false);
										return;
									}
									launchChooseLockscreen(123, true);
								}
							})
					.setNegativeButton("删除",
							new DialogInterface.OnClickListener() {
								public void onClick(
										DialogInterface paramAnonymousDialogInterface,
										int paramAnonymousInt) {
									bindAndRemoveAll();
									isNeedSwitchStateChanged = true;
									addFingerPrint(true);
								}
							}).create();
		}

		public void show(FragmentManager paramFragmentManager,
				String paramString) {
			if (paramFragmentManager.findFragmentByTag(paramString) == null) {
				super.show(paramFragmentManager, paramString);
			}
		}
	}
}
