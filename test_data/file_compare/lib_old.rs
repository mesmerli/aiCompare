#[derive(Debug, Clone, PartialEq)]
pub enum Command {
    Ping,
    Echo(String),
    Quit,
}

pub fn parse_input(input: &str) -> Option<Command> {
    let parts: Vec<&str> = input.trim().split_whitespace().collect();
    if parts.is_empty() {
        return None;
    }

    match parts[0] {
        "PING" => Some(Command::Ping),
        "ECHO" => {
            if parts.len() > 1 {
                Some(Command::Echo(parts[1..].join(" ")))
            } else {
                None
            }
        }
        "QUIT" => Some(Command::Quit),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse() {
        assert_eq!(parse_input("PING"), Some(Command::Ping));
        assert_eq!(parse_input("ECHO Hello"), Some(Command::Echo("Hello".to_string())));
    }
}
