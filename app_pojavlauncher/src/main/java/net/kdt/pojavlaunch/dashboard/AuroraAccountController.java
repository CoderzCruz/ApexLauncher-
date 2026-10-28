package net.kdt.pojavlaunch.dashboard;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.microsoft.PresentedException;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages active and saved Minecraft accounts for the Aurora Glass Dashboard,
 * bridging Microsoft OAuth login, local offline accounts, and skin head loading.
 */
public class AuroraAccountController {

    public interface AccountStateListener {
        void onAccountChanged(@Nullable MinecraftAccount account, @NonNull List<String> allAccountNames);
        void onLoginProgress(int step, int maxSteps);
    }

    private static final int MAX_LOGIN_STEP = 5;

    private final Context mContext;
    private final List<String> mAccountNames = new ArrayList<>();
    private final List<AccountStateListener> mListeners = new ArrayList<>();
    private MinecraftAccount mSelectedAccount;

    private final ProgressListener mProgressListener = new ProgressListener() {
        @Override
        public void onLoginProgress(int step) {
            Tools.runOnUiThread(() -> notifyProgress(step));
        }
    };

    private final DoneListener mDoneListener = new DoneListener() {
        @Override
        public void onLoginDone(MinecraftAccount account) {
            Tools.runOnUiThread(() -> {
                Toast.makeText(mContext, R.string.main_login_done, Toast.LENGTH_SHORT).show();
                PojavProfile.setCurrentProfile(mContext, account.username);
                mSelectedAccount = account;
                reloadAccounts();
            });
            if (!account.isLocal()) {
                PojavApplication.sExecutorService.execute(() -> {
                    account.updateSkinFace();
                    Tools.runOnUiThread(AuroraAccountController.this::notifyAccountChanged);
                });
            }
        }
    };

    private final ErrorListener mErrorListener = new ErrorListener() {
        @Override
        public void onLoginError(Throwable errorMessage) {
            Tools.runOnUiThread(() -> {
                notifyProgress(MAX_LOGIN_STEP);
                if (errorMessage instanceof PresentedException) {
                    PresentedException exception = (PresentedException) errorMessage;
                    Throwable cause = exception.getCause();
                    if (cause == null) {
                        Tools.dialog(mContext, mContext.getString(R.string.global_error), exception.toString(mContext));
                    } else {
                        Tools.showError(mContext, exception.toString(mContext), cause);
                    }
                } else {
                    Tools.showError(mContext, errorMessage);
                }
            });
        }
    };

    private final ExtraListener<Uri> mMicrosoftLoginListener = new ExtraListener<Uri>() {
        @Override
        public boolean onValueSet(String key, @NonNull Uri value) {
            String code = value.getQueryParameter("code");
            if (code != null) {
                new MicrosoftBackgroundLogin(false, code)
                        .performLogin(mProgressListener, mDoneListener, mErrorListener);
            }
            return false;
        }
    };

    private final ExtraListener<String[]> mMojangLoginListener = new ExtraListener<String[]>() {
        @Override
        public boolean onValueSet(String key, @NonNull String[] value) {
            if (value.length >= 2 && value[1].isEmpty()) {
                MinecraftAccount account = new MinecraftAccount();
                account.username = value[0];
                try {
                    account.save();
                } catch (IOException e) {
                    Log.e("AuroraAccountCtrl", "Failed to save local account", e);
                }
                mDoneListener.onLoginDone(account);
            }
            return false;
        }
    };

    public AuroraAccountController(@NonNull Context context) {
        mContext = context;
        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, mMojangLoginListener);
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, mMicrosoftLoginListener);
        reloadAccounts();
    }

    public void destroy() {
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.MOJANG_LOGIN_TODO, mMojangLoginListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.MICROSOFT_LOGIN_TODO, mMicrosoftLoginListener);
        mListeners.clear();
    }

    public void addListener(@NonNull AccountStateListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
        listener.onAccountChanged(mSelectedAccount, Collections.unmodifiableList(mAccountNames));
    }

    public void removeListener(@NonNull AccountStateListener listener) {
        mListeners.remove(listener);
    }

    public void reloadAccounts() {
        mAccountNames.clear();
        File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
        if (accountFolder.exists()) {
            String[] files = accountFolder.list();
            if (files != null) {
                for (String fileName : files) {
                    if (fileName.endsWith(".json") && fileName.length() > 5) {
                        mAccountNames.add(fileName.substring(0, fileName.length() - 5));
                    }
                }
            }
        }
        Collections.sort(mAccountNames, String.CASE_INSENSITIVE_ORDER);

        MinecraftAccount current = PojavProfile.getCurrentProfileContent(mContext, null);
        if (current == null && !mAccountNames.isEmpty()) {
            for (String candidate : mAccountNames) {
                MinecraftAccount loaded = MinecraftAccount.load(candidate);
                if (loaded != null) {
                    current = loaded;
                    PojavProfile.setCurrentProfile(mContext, candidate);
                    break;
                }
            }
        }
        mSelectedAccount = current;

        if (mSelectedAccount != null && mSelectedAccount.isMicrosoft
                && System.currentTimeMillis() > mSelectedAccount.expiresAt) {
            new MicrosoftBackgroundLogin(true, mSelectedAccount.msaRefreshToken)
                    .performLogin(mProgressListener, mDoneListener, mErrorListener);
        }

        notifyAccountChanged();
    }

    public void selectAccount(@NonNull String username) {
        MinecraftAccount loaded = MinecraftAccount.load(username);
        if (loaded == null) {
            removeAccount(username);
            return;
        }
        PojavProfile.setCurrentProfile(mContext, username);
        mSelectedAccount = loaded;
        if (loaded.isMicrosoft && System.currentTimeMillis() > loaded.expiresAt) {
            new MicrosoftBackgroundLogin(true, loaded.msaRefreshToken)
                    .performLogin(mProgressListener, mDoneListener, mErrorListener);
        }
        notifyAccountChanged();
    }

    public void removeAccount(@NonNull String username) {
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, username + ".json");
        if (accountFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            accountFile.delete();
        }
        if (mSelectedAccount != null && username.equals(mSelectedAccount.username)) {
            PojavProfile.setCurrentProfile(mContext, null);
            mSelectedAccount = null;
        }
        reloadAccounts();
    }

    @Nullable
    public MinecraftAccount getSelectedAccount() {
        return mSelectedAccount;
    }

    @NonNull
    public List<String> getSavedAccountNames() {
        return Collections.unmodifiableList(mAccountNames);
    }

    private void notifyAccountChanged() {
        List<String> copy = Collections.unmodifiableList(new ArrayList<>(mAccountNames));
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onAccountChanged(mSelectedAccount, copy);
        }
    }

    private void notifyProgress(int step) {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onLoginProgress(step, MAX_LOGIN_STEP);
        }
    }
}
