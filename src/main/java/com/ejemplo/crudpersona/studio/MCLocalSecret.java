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

public final class MCLocalSecret {
    private final Path MCsecret, MCkeyFile;
    private final boolean MCwindows=System.getProperty("os.name","").toLowerCase().contains("win");
    public MCLocalSecret(Path MCdirectory){this(MCdirectory,"factiliza.secret");}
    public MCLocalSecret(Path MCdirectory,String MCfileName){if(!MCfileName.matches("[a-zA-Z0-9._-]+"))throw new IllegalArgumentException("Nombre de credencial inválido.");MCsecret=MCdirectory.resolve(MCfileName);MCkeyFile=MCdirectory.resolve(".local-secret.key");}
    public boolean MCconfigured(){return Files.exists(MCsecret);}
    public void MCsave(String MCtoken)throws Exception{
        if(MCtoken==null||MCtoken.isBlank())throw new IllegalArgumentException("Escriba el token.");
        byte[] MCvalue=MCtoken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] MCprotectedData=MCwindows?MCdpapi(MCvalue,true):MCaes(MCvalue,true);
        Files.write(MCsecret,MCprotectedData,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        MCrestrict(MCsecret);
    }
    public String MCload()throws Exception{
        if(!MCconfigured())return null;
        byte[] MCdata=Files.readAllBytes(MCsecret);
        return new String(MCwindows?MCdpapi(MCdata,false):MCaes(MCdata,false),StandardCharsets.UTF_8);
    }
    public void MCdelete()throws Exception{Files.deleteIfExists(MCsecret);}
    private static void MCrestrict(Path MCfile){try{Files.setPosixFilePermissions(MCfile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(Exception MCignored){}}
    private byte[] MCaes(byte[] MCinput,boolean MCencrypt)throws Exception{
        byte[] MCkey;
        if(Files.exists(MCkeyFile))MCkey=Files.readAllBytes(MCkeyFile);
        else if(MCencrypt){KeyGenerator MCg=KeyGenerator.getInstance("AES");MCg.init(256);MCkey=MCg.generateKey().getEncoded();Files.write(MCkeyFile,MCkey,StandardOpenOption.CREATE_NEW);MCrestrict(MCkeyFile);}
        else throw new IllegalStateException("No se encuentra la clave local del token.");
        SecretKey MCk=new SecretKeySpec(MCkey,"AES");Cipher MCcipher=Cipher.getInstance("AES/GCM/NoPadding");
        if(MCencrypt){byte[] MCiv=new byte[12];new SecureRandom().nextBytes(MCiv);MCcipher.init(Cipher.ENCRYPT_MODE,MCk,new GCMParameterSpec(128,MCiv));byte[] MCencrypted=MCcipher.doFinal(MCinput);byte[] MCout=new byte[MCiv.length+MCencrypted.length];System.arraycopy(MCiv,0,MCout,0,MCiv.length);System.arraycopy(MCencrypted,0,MCout,MCiv.length,MCencrypted.length);return MCout;}
        if(MCinput.length<29)throw new IllegalArgumentException("Token local dañado.");byte[] MCiv=java.util.Arrays.copyOfRange(MCinput,0,12);MCcipher.init(Cipher.DECRYPT_MODE,MCk,new GCMParameterSpec(128,MCiv));return MCcipher.doFinal(MCinput,12,MCinput.length-12);
    }
    private static byte[] MCdpapi(byte[] MCinput,boolean MCencrypt)throws Exception{
        String MCoperation=MCencrypt?"Protect":"Unprotect";
        String MCscript="Add-Type -AssemblyName System.Security;$b=[Convert]::FromBase64String([Console]::In.ReadToEnd());$o=[Security.Cryptography.ProtectedData]::"+MCoperation+"($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($o))";
        Process MCprocess=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",MCscript).start();
        try(var MCout=MCprocess.getOutputStream()){MCout.write(Base64.getEncoder().encode(MCinput));}
        if(!MCprocess.waitFor(15,TimeUnit.SECONDS)){MCprocess.destroyForcibly();throw new IllegalStateException("DPAPI no respondió.");}
        if(MCprocess.exitValue()!=0)throw new IllegalStateException("No se pudo proteger el token con DPAPI.");
        return Base64.getDecoder().decode(MCprocess.getInputStream().readAllBytes());
    }
}
