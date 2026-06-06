import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.temporal.Temporal;
import java.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.crypto.Cipher; 
import javax.crypto.SecretKey;
import javax.crypto.spec.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
class Key_Handler{
    String algoritmo;
    KeyPairGenerator gerador;
    PublicKey chave_publica;
    PrivateKey chave_privada;
    public Key_Handler(String algoritmo, int qtdBits){
        this.algoritmo = algoritmo;
        try {
            this.gerador = KeyPairGenerator.getInstance(algoritmo); 
            gerador.initialize(qtdBits);
            KeyPair par_chaves = gerador.generateKeyPair();
            //gera as duas chaves
            this.chave_publica = par_chaves.getPublic();
            this.chave_privada = par_chaves.getPrivate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //gera as chaves pública e privadas e as armazena em arquivos
    public void criar_arquivos(String saida_chave_publica, String saida_chave_privada){ 
        //escreve as chaves em arquivos, para que não permaneçam apenas na memória
        try {
                //escreve a chave pública num arquivo de texto
                FileOutputStream saida_publica = new FileOutputStream(saida_chave_publica);
                saida_publica.write(chave_publica.getEncoded()); 
                saida_publica.close();
                FileOutputStream saida_privada = new FileOutputStream(saida_chave_privada);
                saida_privada.write(chave_privada.getEncoded());
                saida_privada.close();  
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("ERRO");
            }
    }
    public KeyPair lerChave(String chavePublicaFile, String chavePrivadaFile){  
        KeyPair chaves;
        try { 
            //usando a classe file, trás o arquivo da chave para a JVM
            File public_key_file = new File(chavePublicaFile); 
            //cria um gerador de chaves
            KeyFactory gerador_chaves = KeyFactory.getInstance(algoritmo);
            //se o arquivo existe e a leitura está ativada para ele:
            if(!public_key_file.exists() || !public_key_file.canRead()){return null;}

            //lê todos os bytes do arquivo
            byte[] bytes_public_key_file = Files.readAllBytes(public_key_file.toPath());
            //usa a classe EncodedKeySpec para gerar um "spec" a partir dos
            //bytes do arquivo da chave
            EncodedKeySpec spec_public = new X509EncodedKeySpec(bytes_public_key_file);
            PublicKey public_key = gerador_chaves.generatePublic(spec_public);
                
            //CHAVE PRIVADA
            File private_key_file = new File(chavePrivadaFile); 
            if(!private_key_file.canRead() && !private_key_file.exists()){return null;}

            byte[] bytes_private_key_file = Files.readAllBytes(private_key_file.toPath());
            EncodedKeySpec spec_private = new PKCS8EncodedKeySpec(bytes_private_key_file);
            PrivateKey private_key = gerador_chaves.generatePrivate(spec_private);
            chaves = new KeyPair(public_key, private_key);
            return chaves;
        } catch (Exception e) {
            e.printStackTrace();
        }   
        return null;  
    }   
}
class EncryptDecryptRSA{
    public void encrypt(PublicKey public_key, SecretKey aes_secret_key, String encrypted_secret_key_path){
        try(
            FileOutputStream fos = new FileOutputStream(encrypted_secret_key_path);
        ) {

            Cipher encrypter = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            encrypter.init(Cipher.ENCRYPT_MODE, public_key);
            //necessário receber a versão binária do arquivo  
            byte[] bytes_aes_key = aes_secret_key.getEncoded();
            byte[] aes_key_encrypted = encrypter.doFinal(bytes_aes_key);
            fos.write(aes_key_encrypted); 

        } catch (Exception e){ 
            e.printStackTrace();
        }
    }  
    public SecretKey decrypt(File encrypted_aes_key, PrivateKey rsaPrivateKey){
        try{
            Cipher decrypter = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            decrypter.init(Cipher.DECRYPT_MODE, rsaPrivateKey );
            byte[] bytes_encrypted_aes_key = Files.readAllBytes(encrypted_aes_key.toPath()); 
            byte[] bytes_decrypted_aes_key = decrypter.doFinal(bytes_encrypted_aes_key); 
            SecretKeySpec aes_key_spec = new SecretKeySpec(bytes_decrypted_aes_key, "AES");
            return aes_key_spec;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
} 
class EncryptDecryptAES{ 
    //parâmetro N é a quantidade de bits, ex : 128, 256
    public SecretKey gerarSenha(int n) throws Exception{
        KeyGenerator gerador_chaves = KeyGenerator.getInstance("AES"); 
        gerador_chaves.init(n); 
        SecretKey chave_secreta = gerador_chaves.generateKey();
        return chave_secreta;
    }
    public SecretKey gerarSenha(String senha, String salt) throws Exception{
        SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        //256 é a quantidade de bits da chave, 65536 iterações
        KeySpec specs = new PBEKeySpec(senha.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey chave_secreta = new SecretKeySpec(fabrica.generateSecret(specs).getEncoded(), "AES"); 
        return chave_secreta;
    }
    public byte[] gerarIv(){
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    public void encrypt(File input_file, byte[] iv, SecretKey key, String output_path){
        try(
            FileOutputStream fos = new FileOutputStream(output_path);
        ) { 
            GCMParameterSpec iv_spec = new GCMParameterSpec(128, iv);
            Cipher encrypter = Cipher.getInstance("AES/GCM/NoPadding");
            encrypter.init(Cipher.ENCRYPT_MODE, key, iv_spec);
            byte[] bytes_file = Files.readAllBytes(input_file.toPath());
            byte[] encrypted_file = encrypter.doFinal(bytes_file);
            fos.write(iv);
            fos.write(encrypted_file); 
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void decrypt(File encrypted_file, SecretKey key, String output_path){
        try (
            FileOutputStream fos = new FileOutputStream(output_path);
            BufferedInputStream  bis = new BufferedInputStream(new FileInputStream(encrypted_file)); 
        ) {
            byte[] iv = new byte[12];
            bis.read(iv);
            GCMParameterSpec iv_spec = new GCMParameterSpec(128, iv);
            Cipher decrypter = Cipher.getInstance("AES/GCM/NoPadding");
            decrypter.init(Cipher.DECRYPT_MODE, key, iv_spec);  
            //criar acumulador
            ByteArrayOutputStream acumulador = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int i;
            while((i = bis.read(buffer)) != -1){
                acumulador.write(
                    buffer,
                    0,
                    i
                );
            }
            byte[] encrypted_file_bytes = acumulador.toByteArray();
            byte[] decrypted_file = decrypter.doFinal(encrypted_file_bytes);
            fos.write(decrypted_file);

        
        } catch (Exception e) {
            e.printStackTrace(); 
        }

    }
}
public class crypto{    


    public static void main(String[] args) {
        Key_Handler gerenciador = new Key_Handler("RSA", 2048);
        String filename_chavepublica = "chave_publica";
        String filename_chaveprivada = "chave_privada";
        gerenciador.criar_arquivos(filename_chavepublica, filename_chaveprivada);
        KeyPair chaves = gerenciador.lerChave(filename_chavepublica, filename_chaveprivada);     
        PublicKey pub_key = chaves.getPublic();
        PrivateKey priv_key = chaves.getPrivate(); 
        String arquivo_teste = "teste.txt";
        EncryptDecryptRSA tech = new EncryptDecryptRSA();
    }
}