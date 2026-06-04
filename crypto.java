import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.temporal.Temporal;
import java.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.crypto.Cipher;  

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
class EncryptDecrypt{
    public void encrypt(PublicKey public_key, String file_to_be_encrypted, String output){
        try(
            FileOutputStream fos = new FileOutputStream(output);
        ) {

            Cipher encrypter = Cipher.getInstance("RSA");
            encrypter.init(Cipher.ENCRYPT_MODE, public_key);
            //necessário receber a versão binária do arquivo  
            File arquivo = new File(file_to_be_encrypted); 
            if(!arquivo.exists() || !arquivo.canRead()){
                return;
            }
            byte[] arquivo_bytes = Files.readAllBytes(arquivo.toPath());
            byte[] arquivo_encriptado = encrypter.doFinal(arquivo_bytes);
            String arquivo_codificado = Base64.getEncoder().encodeToString(arquivo_encriptado);
            byte[] bytes_arquivo_codificado = arquivo_codificado.getBytes(StandardCharsets.UTF_8);
            fos.write(bytes_arquivo_codificado);
            fos.close();   

        } catch (Exception e){ 
            e.printStackTrace();
        }
    }
    public void decrypt(PrivateKey private_key, String encrypted_file, String decrypted_file){ 
        try(
            FileOutputStream fos = new FileOutputStream(decrypted_file);
        ) {
            Cipher decrypter = Cipher.getInstance("RSA");
            decrypter.init(Cipher.DECRYPT_MODE, private_key);
            File arquivo_encriptado = new File(encrypted_file);
            if(!arquivo_encriptado.exists() || !arquivo_encriptado.canRead()){
                return;
            }
            byte[] bytes_arquivo = Files.readAllBytes(arquivo_encriptado.toPath()); 
            byte[] arquivo_desencodado = Base64.getDecoder().decode(bytes_arquivo);
            byte[] arquivo_desencriptado = decrypter.doFinal(arquivo_desencodado);
            String plaintext = new String(arquivo_desencriptado, StandardCharsets.UTF_8);
            byte[] bytes_texto = plaintext.getBytes(StandardCharsets.UTF_8);
            fos.write(bytes_texto); 
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
        EncryptDecrypt tech = new EncryptDecrypt();
        tech.encrypt(pub_key, arquivo_teste, "arquivo_teste_encriptado.txt");
        tech.decrypt(priv_key, "arquivo_teste_encriptado.txt", "arquivo_teste_decriptado.txt");
    }
}