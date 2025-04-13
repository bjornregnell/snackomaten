package snackomaten 

@main def ClientMain(args: String*) = 
  Concurrent.Run:
    if args.isEmpty || args.contains("--gui") then 
      SwingClient(args.filterNot(_ == "--gui").filterNot(_ == "--tui"))

  if args.contains("--tui") || args.nonEmpty && !args.contains("--gui") then 
    TerminalClient(args.filterNot(_ == "--gui").filterNot(_ == "--tui"))

  
