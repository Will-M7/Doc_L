package com.ejemplo.crudpersona.studio;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** @author MC */
public final class MCLocalSecret {
    private final Path mcSecret, mcKeyFile;
    private final boolean mcWindows=System.getProperty("os.name","").toLowerCase().contains("win");
    public MCLocalSecret(Path mcDirectory){this(mcDirectory,"factiliza.secret");}
    public MCLocalSecret(Path mcDirectory,String mcFileName){if(!mcFileName.matches("[a-zA-Z0-9._-]+"))throw new IllegalArgumentException("Nombre de credencial inválido.");mcSecret=mcDirectory.resolve(mcFileName);mcKeyFile=mcDirectory.resolve(".local-secret.key");}
    public boolean mcConfigured(){return Files.exists(mcSecret);}
    public void mcSave(String mcToken)throws Exception{
        if(mcToken==null||mcToken.isBlank())throw new IllegalArgumentException("Escriba el token.");
        byte[] mcValue=mcToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] mcProtectedData=mcWindows?mcDpapi(mcValue,true):mcAes(mcValue,true);
        Files.write(mcSecret,mcProtectedData,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        mcRestrict(mcSecret);
    }
    public String mcLoad()throws Exception{
        if(!mcConfigured())return null;
        byte[] mcData=Files.readAllBytes(mcSecret);
        return new String(mcWindows?mcDpapi(mcData,false):mcAes(mcData,false),StandardCharsets.UTF_8);
    }
    public void mcDelete()throws Exception{Files.deleteIfExists(mcSecret);}
    private static void mcRestrict(Path mcFile){try{Files.setPosixFilePermissions(mcFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(Exception mcIgnored){}}
    private byte[] mcAes(byte[] mcInput,boolean mcEncrypt)throws Exception{
        byte[] mcKey;
        if(Files.exists(mcKeyFile))mcKey=Files.readAllBytes(mcKeyFile);
        else if(mcEncrypt){KeyGenerator mcG=KeyGenerator.getInstance("AES");mcG.init(256);mcKey=mcG.generateKey().getEncoded();Files.write(mcKeyFile,mcKey,StandardOpenOption.CREATE_NEW);mcRestrict(mcKeyFile);}
        else throw new IllegalStateException("No se encuentra la clave local del token.");
        SecretKey mcK=new SecretKeySpec(mcKey,"AES");Cipher mcCipher=Cipher.getInstance("AES/GCM/NoPadding");
        if(mcEncrypt){byte[] mcIv=new byte[12];new SecureRandom().nextBytes(mcIv);mcCipher.init(Cipher.ENCRYPT_MODE,mcK,new GCMParameterSpec(128,mcIv));byte[] mcEncrypted=mcCipher.doFinal(mcInput);byte[] mcOut=new byte[mcIv.length+mcEncrypted.length];System.arraycopy(mcIv,0,mcOut,0,mcIv.length);System.arraycopy(mcEncrypted,0,mcOut,mcIv.length,mcEncrypted.length);return mcOut;}
        if(mcInput.length<29)throw new IllegalArgumentException("Token local dañado.");byte[] mcIv=java.util.Arrays.copyOfRange(mcInput,0,12);mcCipher.init(Cipher.DECRYPT_MODE,mcK,new GCMParameterSpec(128,mcIv));return mcCipher.doFinal(mcInput,12,mcInput.length-12);
    }
    private static byte[] mcDpapi(byte[] mcInput,boolean mcEncrypt)throws Exception{
        String mcOperation=mcEncrypt?"Protect":"Unprotect";
        String mcScript="$b=[Convert]::FromBase64String([Console]::In.ReadToEnd());$o=[Security.Cryptography.ProtectedData]::"+mcOperation+"($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($o))";
        Process mcProcess=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",mcScript).start();
        try(var mcOut=mcProcess.getOutputStream()){mcOut.write(Base64.getEncoder().encode(mcInput));}
        if(!mcProcess.waitFor(15,TimeUnit.SECONDS)){mcProcess.destroyForcibly();throw new IllegalStateException("DPAPI no respondió.");}
        if(mcProcess.exitValue()!=0)throw new IllegalStateException("No se pudo proteger el token con DPAPI.");
        return Base64.getDecoder().decode(mcProcess.getInputStream().readAllBytes());
    }
}
