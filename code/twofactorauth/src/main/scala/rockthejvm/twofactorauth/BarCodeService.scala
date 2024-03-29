package rockthejvm.twofactorauth

import java.io.FileOutputStream

import cats.effect.IO
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.{BarcodeFormat, MultiFormatWriter}
import rockthejvm.twofactorauth.Generator._

object BarCodeService {
  def getGoogleAuthenticatorBarCode(
      secretKey: String,
      account: String,
      issuer: String,
      generator: Generator,
      counter: Int
  ): String =
    generator match {
      case Hotp =>
        s"otpauth://hotp/$issuer:$account?secret=$secretKey&issuer=$issuer&algorithm=SHA256&counter=$counter"
      case Totp =>
        s"otpauth://totp/$issuer:$account?secret=$secretKey&issuer=$issuer&algorithm=SHA256"
    }

  def createQRCode(
      barCodeData: String,
      filePath: String = "barCode.png",
      height: Int = 400,
      width: Int = 400
  ): IO[Unit] =
    IO {
      val matrix: BitMatrix        = new MultiFormatWriter().encode(
        barCodeData,
        BarcodeFormat.QR_CODE,
        width,
        height
      )
      val outVal: FileOutputStream = new FileOutputStream(filePath)
      MatrixToImageWriter.writeToStream(matrix, "png", outVal)
    }
}
