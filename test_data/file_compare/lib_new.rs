#[derive(Debug, Clone, PartialEq)]
pub enum Command {
    Ping,
    Echo(String),
    SystemInfo { verbose: bool },
    Shutdown { delay_secs: u64 },
}

#[derive(Debug, PartialEq)]
pub enum ParseError {
    EmptyInput,
    UnknownCommand(String),
    MissingArgument(String),
}

pub fn parse_input(input: &str) -> Result<Command, ParseError> {
    let parts: Vec<&str> = input.trim().split_whitespace().collect();
    if parts.is_empty() {
        return Err(ParseError::EmptyInput);
    }

    match parts[0].to_uppercase().as_str() {
        "PING" => Ok(Command::Ping),
        "ECHO" => {
            if parts.len() > 1 {
                Ok(Command::Echo(parts[1..].join(" ")))
            } else {
                Err(ParseError::MissingArgument("ECHO command requires a message body".to_string()))
            }
        }
        "SYS" => {
            let verbose = parts.get(1).map(|&arg| arg == "-v").unwrap_or(false);
            Ok(Command::SystemInfo { verbose })
        }
        "SHUTDOWN" => {
            let delay = parts.get(1)
                .and_then(|&val| val.parse::<u64>().ok())
                .unwrap_or(0);
            Ok(Command::Shutdown { delay_secs: delay })
        }
        cmd => Err(ParseError::UnknownCommand(cmd.to_string())),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse() {
        assert_eq!(parse_input("PING"), Ok(Command::Ping));
        assert_eq!(parse_input("ECHO Hello"), Ok(Command::Echo("Hello".to_string())));
        assert_eq!(parse_input("SYS -v"), Ok(Command::SystemInfo { verbose: true }));
        assert_eq!(parse_input("SHUTDOWN 10"), Ok(Command::Shutdown { delay_secs: 10 }));
    }
}
