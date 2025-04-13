package snackomaten

object SwingClient:
  def apply(args: Seq[String]) = 
    javax.swing.JOptionPane.showMessageDialog(null, s"TODO:\nsnackomaten.SwingClient\nargs=${args}")
