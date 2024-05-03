package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.BalanceHistoryRepository;
import com.shepherdmoney.interviewproject.repository.CreditCardRepository;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import jakarta.jws.soap.SOAPBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class CreditCardController {

    // TODO: wire in CreditCard repository here (~1 line)
    @Autowired
    private CreditCardRepository creditCardRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        // TODO: Create a credit card entity, and then associate that credit card with user with given userId
        //       Return 200 OK with the credit card id if the user exists and credit card is successfully associated with the user
        //       Return other appropriate response code for other exception cases
        //       Do not worry about validating the card number, assume card number could be any arbitrary format and length
        CreditCard creditCard = new CreditCard();
        creditCard.setNumber(payload.getCardNumber());
        creditCard.setIssuanceBank(payload.getCardIssuanceBank());
        creditCard.setUserid(payload.getUserId());
        creditCard.setBalanceHistoryIds(new ArrayList<>());
        Optional<User> optionalUser = userRepository.findById(payload.getUserId());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            CreditCard newCreditCard = creditCardRepository.save(creditCard);
            user.addCard(newCreditCard.getId());
            userRepository.save(user);
            return ResponseEntity.ok().body(null);
        }
        return ResponseEntity.badRequest().body(null);
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        // TODO: return a list of all credit card associated with the given userId, using CreditCardView class
        //       if the user has no credit card, return empty list, never return null
        List<CreditCardView> allCardsOfUser = new ArrayList<>();
        Optional<User> optionalUser = userRepository.findById(userId);
        if(optionalUser.isPresent()) {
            User user = optionalUser.get();
            for(int creditCardId:user.getCreditCardIds()) {
                Optional<CreditCard> optionalCreditCard = creditCardRepository.findById(creditCardId);
                if (optionalCreditCard.isPresent()) {
                    CreditCard creditCard = optionalCreditCard.get();
                    CreditCardView creditCardView = new CreditCardView(creditCard.getIssuanceBank(), creditCard.getNumber());
                    allCardsOfUser.add(creditCardView);
                }
            }
        }
        return ResponseEntity.ok(allCardsOfUser);
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        // TODO: Given a credit card number, efficiently find whether there is a user associated with the credit card
        //       If so, return the user id in a 200 OK response. If no such user exists, return 400 Bad
        Optional<CreditCard> creditCard = creditCardRepository.findByCreditCardNumber(creditCardNumber);
        if (creditCard.isPresent()) {
            return ResponseEntity.ok(creditCard.get().getUserid());
        }
        return ResponseEntity.badRequest().body(null);
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<Integer> updateBalance(@RequestBody UpdateBalancePayload[] payload) {
        //TODO: Given a list of transactions, update credit cards' balance history.
        //      1. For the balance history in the credit card
        //      2. If there are gaps between two balance dates, fill the empty date with the balance of the previous date
        //      3. Given the payload `payload`, calculate the balance different between the payload and the actual balance stored in the database
        //      4. If the different is not 0, update all the following budget with the difference
        //      For example: if today is 4/12, a credit card's balanceHistory is [{date: 4/12, balance: 110}, {date: 4/10, balance: 100}],
        //      Given a balance amount of {date: 4/11, amount: 110}, the new balanceHistory is
        //      [{date: 4/12, balance: 120}, {date: 4/11, balance: 110}, {date: 4/10, balance: 100}]
        //      This is because
        //      1. You would first populate 4/11 with previous day's balance (4/10), so {date: 4/11, amount: 100}
        //      2. And then you observe there is a +10 difference
        //      3. You propagate that +10 difference until today
        //      Return 200 OK if update is done and successful, 400 Bad Request if the given card number
        //        is not associated with a card.


        // According to the updated Readme, I am filling empty dates balances with balances of previous dates.
        // Assumed that the dates in payload are always greater than latest balance history date of a card.
        // Adding the payload to balance history and simultaneously updating the balance history ids of credit cards.
        try {
            // Used for loop to perform functions on each payload
            for (UpdateBalancePayload currentPayload : payload) {
                Optional<CreditCard> optionalCreditCard = creditCardRepository.findByCreditCardNumber(currentPayload.getCreditCardNumber());
                if (optionalCreditCard.isEmpty()) {
                    return ResponseEntity.badRequest().body(null);
                }
                Optional<CreditCard> creditCard = creditCardRepository.findByCreditCardNumber(currentPayload.getCreditCardNumber());
                CreditCard card = creditCard.get();
                List<Integer> historyIds = card.getBalanceHistoryIds();
                int historyLength = historyIds.size();
                BalanceHistory newBalance = new BalanceHistory();
                newBalance.setBalance(currentPayload.getBalanceAmount());
                newBalance.setDate(currentPayload.getBalanceDate());
                //if there is no balance history of a card then we add the new balance history directly
                if(historyLength==0) {
                    newBalance = balanceHistoryRepository.save(newBalance);
                    historyIds.add(newBalance.getId());
                    creditCardRepository.save(card);
                    continue;
                }
                // if there is already balance history of a card then we get the latest balance history of the card
                Optional<BalanceHistory> optionalCurrentBalance = balanceHistoryRepository.findById(historyIds.get(historyLength-1));
                if (optionalCurrentBalance.isEmpty()) {
                    return ResponseEntity.badRequest().body(null);
                }
                BalanceHistory currentBalance = optionalCurrentBalance.get();
                LocalDate nextDate = currentBalance.getDate().plusDays(1);
                // if the payload date is less than or equal to current balance history date we continue
                if (!newBalance.getDate().isAfter(currentBalance.getDate())) {
                    continue;
                }
                // now if there are any empty dates between the new balance history and the latest or current balance history of the card we use a while loop to fill the empty date balances with current balances
                while (!nextDate.isEqual(newBalance.getDate())) {
                    BalanceHistory emptyDateBalanceHistory = new BalanceHistory();
                    emptyDateBalanceHistory.setBalance(currentBalance.getBalance());
                    emptyDateBalanceHistory.setDate(nextDate);
                    emptyDateBalanceHistory = balanceHistoryRepository.save(emptyDateBalanceHistory);
                    historyIds.add(emptyDateBalanceHistory.getId());
                    nextDate = nextDate.plusDays(1);
                }
                newBalance = balanceHistoryRepository.save(newBalance);
                historyIds.add(newBalance.getId());
                creditCardRepository.save(card);
            }
            return ResponseEntity.ok().body(null);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }

    }
    
}
