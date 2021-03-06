package com.artur.belogur;

import com.artur.belogur.compare.FlatComparator;
import com.artur.belogur.compare.FlatPriceDiff;
import com.artur.belogur.flatclient.FlatClient;
import com.artur.belogur.notification.LogSender;
import com.artur.belogur.notification.Notifiable;
import com.artur.belogur.notification.SendersHolder;
import com.artur.belogur.notification.TelegramSender;
import com.artur.belogur.repository.FlatRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class FlatScheduler {

    private final SendersHolder sendersHolder;

    public FlatScheduler() {
        Notifiable telegramSender = new TelegramSender();
        Notifiable logSender = new LogSender();
        this.sendersHolder = new SendersHolder(telegramSender, logSender);
    }

    public void run() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(wrappedTask(), 0, 12, TimeUnit.HOURS);
    }

    private Runnable wrappedTask() {
        return () -> {
            try {
                task().run();
            } catch (Exception e) {
                log.info(e.getMessage(), e);
            }
        };

    }

    private Runnable task() {
        return () -> {
            FlatClient client = new FlatClient();
            List<Flat> inputFlats = client.getFlats();

            FlatRepository flatRepository = new FlatRepository();
            FlatComparator comparator = new FlatComparator(flatRepository, inputFlats);

            if (comparator.inputFreeEqual()) {
                sendersHolder.sendFlatNumberNotChanged();
            } else {
                sendersHolder.sendFreeFlatInfo(comparator.inputFreeMore(), getFlatsPrice(comparator.getFreeDiff()));
            }

            if (!comparator.allDesiredIncluded()) {
                sendersHolder.sendDesiredFlatInfo(getFlatsPrice(comparator.getNotIncludedDesired()));
            }

            List<FlatPriceDiff> flatPriceDiffs = comparator.getDiffPrices();
            sendersHolder.sendPriceInfo(flatPriceDiffs.stream()
                    .filter(flatPriceDiff -> flatPriceDiff.getOldPrice() != flatPriceDiff.getNewPrice())
                    .collect(Collectors.toList()));

            flatRepository.saveFreeFlats(inputFlats);
            flatRepository.saveDesiredFlats(flatPriceDiffs.stream()
                    .map(flatPriceDiff -> new Flat(flatPriceDiff.getNumber(), flatPriceDiff.getNewPrice()))
                    .collect(Collectors.toList()));
        };
    }

    private List<Integer> getFlatsPrice(List<Flat> flats) {
        return flats.stream().map(Flat::getNumber).collect(Collectors.toList());
    }
}
