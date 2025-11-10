def main():
    denominations = [1, 5, 10, 20, 50, 100]
    counts = [0] * len(denominations)

    def display_ui():
        print("|   1   |   2   |   3   |   4   |   5   |   6   |")
        print("[1s][5s][10s][20s][50s][100s]")
        print("(" + ")(".join(f"${c * d}" for c, d in zip(counts, denominations)) + ")")
        print(f"Total: ${sum(c * d for c, d in zip(counts, denominations))}")

    while True:
        display_ui()
        user_input = input('Enter deposit and amount (e.g., "1, 6" for 6 one dollar bills, or "q" to quit): ').strip()

        if user_input.lower() in ['q', 'quit', 'exit']:
            print("Exiting...")
            break

        try:
            idx, amt = map(int, user_input.split(','))
            if 1 <= idx <= len(denominations) and amt >= 0:
                counts[idx - 1] += amt
            else:
                print("Invalid index or amount.")
        except ValueError:
            print("Invalid input. Use format like '1, 6'.")

if __name__ == "__main__":
    main()

