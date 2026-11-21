package com.xu.deltaworkshop;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;

final class ModelSettings {
    private static final String ALIAS="delta-model-key";
    private final SharedPreferences prefs;
    ModelSettings(Context c){prefs=c.getSharedPreferences("model",Context.MODE_PRIVATE);}
    String endpoint(){return prefs.getString("endpoint","https://api.deepseek.com");}
    String model(){return prefs.getString("model","deepseek-flash");}
    boolean configured(){return prefs.contains("secret");}
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return gen.generateKey();
    }
    String secret() throws Exception {
        if(!configured())return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("secret",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    void save(String endpoint,String model,String secret) throws Exception {
        java.net.URI uri=java.net.URI.create(endpoint.trim());
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("服务地址需为 HTTPS 地址");
        if(model.trim().isEmpty())throw new IllegalArgumentException("请填写模型名称");
        String normalized=endpoint.trim().replaceAll("/+$","");
        if(configured()&&!normalized.equals(endpoint())&&secret.trim().isEmpty())throw new IllegalArgumentException("更换模型服务时，请填写该服务的密钥");
        SharedPreferences.Editor edit=prefs.edit().putString("endpoint",endpoint.trim().replaceAll("/+$","")).putString("model",model.trim());
        if(!secret.trim().isEmpty()){
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            edit.putString("secret",Base64.encodeToString(cipher.doFinal(secret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        }
        edit.apply();
    }
    void clear(){prefs.edit().remove("secret").remove("iv").apply();}
}
