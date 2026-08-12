@Service
@RequiredArgsConstructor
public class DemandDepositAccountOpeningService
        implements DemandDepositAccountOpeningUseCase {

    private final IssueAccountNumberUseCase issueAccountNumberUseCase;
    private final AccountPersistencePort accountPersistencePort;
    private final Clock clock;

    @Override
    @Transactional
    public AccountOpeningResult open(
            DemandDepositAccountOpeningCommand command
    ) {
        String accountNumber =
                issueAccountNumberUseCase.issue(
                        AccountType.DEMAND_DEPOSIT,
                        null
                );

        LocalDateTime openedDate =
                LocalDateTime.now(clock);

        Account account = Account.open(
                accountNumber,
                command.customerId(),
                null,
                AccountType.DEMAND_DEPOSIT,
                command.passwordHash(),
                openedDate,
                null
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        return new AccountOpeningResult(
                savedAccount.getAccountId(),
                savedAccount.getAccountNumber()
        );
    }
}